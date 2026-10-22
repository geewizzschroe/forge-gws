#!/usr/bin/env python3
"""Landless card-pool audit for the roguelike run mode.

Reads every card script in forge-gui/res/cardsfolder and lists the cards whose behaviour changes when
decks hold no lands (the mode's built-in rule: one chosen basic conjured each turn, 10 lands at most).
Detection is by pattern over the scripts, so it can miss cards or list a card that is fine in practice;
treat it as a starting point for curating the mode's card pools, not as a verdict.

usage: python3 -I landless_audit.py <repo root> <output .md> [<quarantine list .txt>]

With a third argument it also writes the quarantine list: every nonland card in any group, minus the ramp
cards that have landless overrides. The run mode keeps those cards out of its pools until each is fixed.
"""
import json
import os
import re
import sys

LAND_TYPES = r"(?:(?<![Nn]on)Land|Plains|Island|Swamp|Mountain|Forest|Desert|Gate|Cave|Town|Sphere)"
NON_DECK_TYPES = ("Vanguard", "Plane", "Phenomenon", "Scheme", "Conspiracy", "Dungeon", "Attraction",
                  "Contraption", "Stickers", "Base")

# category id -> (title, what happens in landless play, test(card) -> bool)
CATEGORIES = []
TOKEN_SCRIPTS = {}  # token script name -> its ability lines, filled in by main()


def category(cid, title, effect):
    def wrap(fn):
        CATEGORIES.append((cid, title, effect, fn))
        return fn
    return wrap


def lines_with_api(card, api):
    """Ability and SVar lines using the given effect API (SP$/AB$/DB$ X)."""
    pat = re.compile(r"\b(?:SP|AB|DB)\$ " + api + r"\b")
    return [l for l in card["script"] if pat.search(l)]


@category("land_search_battlefield", "Searches the library for lands to put onto the battlefield (ramp)",
          "Finds nothing in a landless deck. Candidates for a landless override (the mode has 10 so far).")
def land_search_bf(card):
    for l in lines_with_api(card, "ChangeZone(?:All)?"):
        if "Origin$ Library" in l and "Destination$ Battlefield" in l and re.search(r"ChangeType\$ [^|]*" + LAND_TYPES, l):
            return True
    return False


@category("land_search_hand", "Searches the library for lands to put into hand (land tutors)",
          "Finds nothing; the card does nothing else unless it has other effects.")
def land_search_hand(card):
    for l in lines_with_api(card, "ChangeZone(?:All)?"):
        if "Origin$ Library" in l and "Destination$ Hand" in l and re.search(r"ChangeType\$ [^|]*" + LAND_TYPES, l):
            return True
    return False


@category("library_dig_lands", "Looks through the library for lands (dig, reveal until, etc.)",
          "Always whiffs on lands; what happens with the misses depends on the card.")
def library_dig(card):
    for l in lines_with_api(card, "(?:Dig|DigUntil|RevealHand|Mill)"):
        if re.search(r"(?:ChangeValid|Valid)\$ [^|]*" + LAND_TYPES, l):
            return True
    return False


@category("landcycling", "Landcycling (cycle to search for a land)",
          "Still discards and costs mana, but finds nothing.")
def landcycling(card):
    return any(re.match(r"K:TypeCycling:(?:Basic|Plains|Island|Swamp|Mountain|Forest|Land)", l) or
               re.match(r"K:(?:Basic )?[Ll]andcycling", l) for l in card["script"])


@category("landfall", "Landfall and other \"whenever a land enters\" triggers",
          "Triggers every turn from the built-in land until you reach 10 lands, then only from ramp; free value early.")
def landfall(card):
    # the card's own triggers, and those of tokens it creates (Choco-Comet's Bird, for example)
    lines = list(card["script"])
    for token in card["tokens"]:
        lines += TOKEN_SCRIPTS.get(token, [])
    for l in lines:
        if "Mode$ ChangesZone" in l and "Destination$ Battlefield" in l and re.search(r"ValidCard\$ [^|]*(?<![Nn]on)Land", l):
            return True
    return False


@category("domain", "Domain (counts basic land types)",
          "Counts only the basic types you chose to conjure; up to 5 if you pick every type.")
def domain(card):
    return any("Count$Domain" in l for l in card["script"])


@category("land_count", "Scales with the number of lands you control",
          "Predictable: grows by one each turn to exactly 10.")
def land_count(card):
    if any(re.search(r"IsPresent\$ Land\.YouCtrl", l) for l in card["script"]):
        return True  # "as long as you control N or more lands"
    return any(re.search(r"Count\$(?:Valid|TypeYouCtrl\.|Type)[^|]*Land[.+A-Za-z]*YouCtrl|Count\$Valid Land\.YouCtrl", l)
               for l in card["script"])


@category("sacrifice_land", "Sacrifices a land (as a cost or an effect)",
          "Cheaper than usual: you get a new land each turn until the cap, and the cap leaves room again.")
def sacrifice_land(card):
    for l in card["script"]:
        if re.search(r"Sac<\d+/" + LAND_TYPES, l) or ("$ Sacrifice" in l and re.search(r"SacValid\$ [^|]*(?<![Nn]on)Land", l)):
            return True
    return False


@category("return_land", "Returns a land to hand as a cost (bounce costs)",
          "The returned basic can be replayed as your land for the turn, if you haven't played one.")
def return_land(card):
    return any(re.search(r"Return<\d+/" + LAND_TYPES, l) for l in card["script"])


@category("extra_land_plays", "Lets you play additional lands",
          "Only matters with lands in hand, which in this mode come only from effects such as the landless Cultivate.")
def extra_land_plays(card):
    return any("AdjustLandPlays" in l for l in card["script"])


@category("land_destruction", "Destroys, exiles or returns lands to hand",
          "Less punishing: the victim gets a land back every turn; mass land destruction resets both players to 0 and they regrow 1 a turn.")
def land_destruction(card):
    for l in lines_with_api(card, "(?:Destroy|DestroyAll|ChangeZone|ChangeZoneAll)"):
        if "Origin$ Library" in l or "Origin$ Graveyard" in l or "Origin$ Hand" in l:
            continue
        if re.search(r"(?:ValidTgts|ValidCards|ChangeType)\$ (?:[^|]*,)?(?:Land|Plains|Island|Swamp|Mountain|Forest)\b", l) and \
                ("$ Destroy" in l or "Destination$ Exile" in l or "Destination$ Hand" in l):
            return True
    return False


@category("land_recursion", "Returns lands from the graveyard",
          "Works only on lands that were sacrificed or destroyed.")
def land_recursion(card):
    for l in lines_with_api(card, "ChangeZone(?:All)?"):
        if "Origin$ Graveyard" in l and re.search(r"(?:ChangeType|ValidTgts)\$ [^|]*" + LAND_TYPES, l):
            return True
    return False


@category("land_back_face", "A spell on the front with a land on the back",
          "Kept by the land stripper; the land side can still be played from hand, counting against the 10-land cap.")
def land_back(card):
    return card["back_is_land"] and not card["front_is_land"]


@category("land_subtypes", "Cares about nonbasic land types (Towns, Gates, Deserts, Caves, Spheres)",
          "Never finds any: the mode's only lands are conjured basics.")
def land_subtypes(card):
    return any(re.search(r"\b(?:Town|Gate|Desert|Cave|Sphere)s?\b", l) for l in card["script"])


def parse(path):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    faces = text.split("\nALTERNATE\n")
    front = faces[0]
    name = re.search(r"^Name:(.*)$", front, re.M)
    types = re.search(r"^Types:(.*)$", front, re.M)
    back_types = re.search(r"^Types:(.*)$", faces[1], re.M) if len(faces) > 1 else None
    mode = re.search(r"^AlternateMode:(.*)$", text, re.M)
    script = [l for l in text.splitlines() if re.match(r"^(?:A|T|S|R|K|SVar):", l)]
    t = types.group(1) if types else ""
    return {
        "name": name.group(1).strip() if name else os.path.basename(path),
        "types": t,
        "front_is_land": bool(re.search(r"\bLand\b", t)),
        "back_is_land": bool(back_types and re.search(r"\bLand\b", back_types.group(1))
                             and mode and mode.group(1).strip() == "Modal"),
        "script": script,
        "tokens": re.findall(r"TokenScript\$ ([\w,]+)", text) and [t for m in re.findall(r"TokenScript\$ ([\w,]+)", text) for t in m.split(",")],
    }


def sample_content_cards(root):
    base = os.path.join(root, "forge-gui/src/main/resources/roguelike")
    names = set()
    for sub in ("starters", "encounters", "pools"):
        d = os.path.join(base, sub)
        for fn in sorted(os.listdir(d)) if os.path.isdir(d) else []:
            with open(os.path.join(d, fn), encoding="utf-8") as f:
                data = json.load(f)
            names.update(data.get("deck", {}).keys())
            names.update(data.get("cards", []))
            if data.get("commander"):
                names.add(data["commander"])
    overrides = set()
    od = os.path.join(base, "overrides")
    for fn in os.listdir(od) if os.path.isdir(od) else []:
        with open(os.path.join(od, fn), encoding="utf-8") as f:
            m = re.search(r"^Name:(.*)$", f.read(), re.M)
            if m:
                overrides.add(m.group(1).strip())
    return names, overrides


def main():
    root, out = sys.argv[1], sys.argv[2]
    folder = os.path.join(root, "forge-gui/res/cardsfolder")
    tokens = os.path.join(root, "forge-gui/res/tokenscripts")
    for fn in os.listdir(tokens):
        if fn.endswith(".txt"):
            with open(os.path.join(tokens, fn), encoding="utf-8") as f:
                TOKEN_SCRIPTS[fn[:-4]] = [l for l in f.read().splitlines() if re.match(r"^(?:A|T|S|R|K|SVar):", l)]
    cards = []
    for dirpath, _, files in os.walk(folder):
        for fn in files:
            if fn.endswith(".txt"):
                cards.append(parse(os.path.join(dirpath, fn)))
    deck_cards = [c for c in cards if not any(t in c["types"].split() for t in NON_DECK_TYPES)]
    lands = [c for c in deck_cards if c["front_is_land"]]
    spells = [c for c in deck_cards if not c["front_is_land"]]
    sample, overrides = sample_content_cards(root)

    found = {}
    for cid, _, _, test in CATEGORIES:
        found[cid] = sorted({c["name"] for c in spells if test(c) and not (cid.startswith("land_search") and c["name"] in overrides)})

    o = []
    o.append("# Landless card-pool audit\n")
    o.append("Generated by `design/roguelike-run-mode/tools/landless_audit.py` from the card scripts in "
             "`forge-gui/res/cardsfolder`. It looks for patterns in the scripts, so it can miss cards and can list "
             "cards that are fine in practice. It is a starting point for choosing the mode's card pools (and for D11), "
             "not a set of decisions.\n")
    o.append("- Cards scanned (excluding Vanguard, planes, schemes and other non-deck cards): %d" % len(deck_cards))
    o.append("- Lands (removed from every deck by the land stripper): %d" % len(lands))
    o.append("- Ramp cards already converted (overrides): %s\n" % (", ".join(sorted(overrides)) or "none"))
    o.append("A card can appear in more than one group.\n")
    o.append("| Group | Cards | What happens in landless play |")
    o.append("|---|---|---|")
    for cid, title, effect, _ in CATEGORIES:
        o.append("| %s | %d | %s |" % (title, len(found[cid]), effect))
    o.append("")

    o.append("## Sample content affected\n")
    hits = []
    for cid, title, _, _ in CATEGORIES:
        names = [n for n in found[cid] if n in sample]
        if names:
            hits.append("- **%s**: %s" % (title, ", ".join(names)))
    o.extend(hits or ["None of the sample content's cards fall into these groups."])
    o.append("")

    for cid, title, effect, _ in CATEGORIES:
        o.append("## %s (%d)\n" % (title, len(found[cid])))
        o.append(effect + "\n")
        o.append(", ".join(found[cid]) if found[cid] else "None found.")
        o.append("")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(o))
    if len(sys.argv) > 3:
        quarantined = sorted({n for names in found.values() for n in names} - overrides)
        with open(sys.argv[3], "w", encoding="utf-8") as f:
            f.write("# Cards affected by landless play, kept out of the run mode's pools until fixed.\n")
            f.write("# Generated by design/roguelike-run-mode/tools/landless_audit.py; do not edit by hand.\n")
            f.write("\n".join(quarantined) + "\n")
        print("%5d  quarantined" % len(quarantined))
    for cid, title, _, _ in CATEGORIES:
        print("%5d  %s" % (len(found[cid]), title))


if __name__ == "__main__":
    main()
