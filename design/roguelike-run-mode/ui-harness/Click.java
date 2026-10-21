import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class Click {
    // usage: Click click x y | Click key NAME | Click type "text" | Click drag x1 y1 x2 y2 | Click swipe x1 y1 x2 y2
    static void tap(Robot r, int k, boolean shift) throws Exception {
        if (shift) r.keyPress(KeyEvent.VK_SHIFT);
        r.keyPress(k); Thread.sleep(25); r.keyRelease(k);
        if (shift) r.keyRelease(KeyEvent.VK_SHIFT);
    }
    public static void main(String[] a) throws Exception {
        Robot r = new Robot();
        r.setAutoDelay(40);
        switch (a[0]) {
            case "click" -> {
                r.mouseMove(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                Thread.sleep(250);
                r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(120);
                r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "key" -> {
                int k = (int) KeyEvent.class.getField("VK_" + a[1]).get(null);
                r.keyPress(k); Thread.sleep(80); r.keyRelease(k);
            }
            case "type" -> {
                for (char c : a[1].toCharArray()) {
                    switch (c) {
                        case ' ' -> tap(r, KeyEvent.VK_SPACE, false);
                        case '-' -> tap(r, KeyEvent.VK_MINUS, false);
                        case '_' -> tap(r, KeyEvent.VK_MINUS, true);
                        case '.' -> tap(r, KeyEvent.VK_PERIOD, false);
                        case ',' -> tap(r, KeyEvent.VK_COMMA, false);
                        case '"' -> tap(r, KeyEvent.VK_QUOTE, true);
                        case '\'' -> tap(r, KeyEvent.VK_QUOTE, false);
                        default -> {
                            boolean up = Character.isUpperCase(c);
                            int k = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
                            tap(r, k, up);
                        }
                    }
                    Thread.sleep(35);
                }
            }
            case "drag" -> {
                r.mouseMove(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                Thread.sleep(200);
                r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(200);
                r.mouseMove(Integer.parseInt(a[3]), Integer.parseInt(a[4]));
                Thread.sleep(300);
                r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "swipe" -> { // a quick fling, for gestures that need velocity
                int x1 = Integer.parseInt(a[1]), y1 = Integer.parseInt(a[2]);
                int x2 = Integer.parseInt(a[3]), y2 = Integer.parseInt(a[4]);
                r.setAutoDelay(0);
                r.mouseMove(x1, y1);
                Thread.sleep(150);
                r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                for (int i = 1; i <= 10; i++) {
                    r.mouseMove(x1 + (x2 - x1) * i / 10, y1 + (y2 - y1) * i / 10);
                    Thread.sleep(8);
                }
                r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
        }
    }
}
