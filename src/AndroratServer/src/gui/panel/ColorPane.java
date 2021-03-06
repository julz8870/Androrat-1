package gui.panel;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.Color;

/**
 * Diese Klasse definiert das Aussehen und den Hintergrund der Panele.
 */
public class ColorPane extends JTextPane {

    public void append(Color c, String s) {
    StyleContext sc = StyleContext.getDefaultStyleContext();
    AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);

    int len = getDocument().getLength();
    setCaretPosition(len);
    setCharacterAttributes(aset, false);
    replaceSelection(s);
  }
}
