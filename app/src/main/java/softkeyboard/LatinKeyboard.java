package softkeyboard;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class LatinKeyboard extends Keyboard {

    private Key enterKey;
    private Key spaceKey;
    private Key changeModeKey;
    private Key languageSwitchKey;
    private Key savedChangeModeKey;
    private Key savedLanguageSwitchKey;
    
    public LatinKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
    }

    public LatinKeyboard(Context context, int layoutTemplateResId, CharSequence characters, int columns, int horizontalPadding) {
        super(context, layoutTemplateResId, characters, columns, horizontalPadding);
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
        Key key = new LatinKey(res, parent, x, y, parser);

        if (key.codes[0] == 10) {
            enterKey = key;
        }
        else if (key.codes[0] == ' ') {
            spaceKey = key;
        }
        else if (key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) {
            changeModeKey = key;
            savedChangeModeKey = new LatinKey(res, parent, x, y, parser);
        }
        else if (key.codes[0] == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            languageSwitchKey = key;
            savedLanguageSwitchKey = new LatinKey(res, parent, x, y, parser);
        }
        return key;
    }

    void setLanguageSwitchKeyVisibility(boolean visible) {
        if (visible) {
            // The language switch key should be visible. Restore the size of the mode change key
            // and language switch key using the saved layout.
            changeModeKey.width = savedChangeModeKey.width;
            changeModeKey.x = savedChangeModeKey.x;
            languageSwitchKey.width = savedLanguageSwitchKey.width;
            languageSwitchKey.icon = savedLanguageSwitchKey.icon;
            languageSwitchKey.iconPreview = savedLanguageSwitchKey.iconPreview;
        }
        else {
            // The language switch key should be hidden. Change the width of the mode change key
            // to fill the space of the language key so that the user will not see any strange gap.
            changeModeKey.width = savedChangeModeKey.width + savedLanguageSwitchKey.width;
            languageSwitchKey.width = 0;
            languageSwitchKey.icon = null;
            languageSwitchKey.iconPreview = null;
        }
    }

    static class LatinKey extends Keyboard.Key {
        
        public LatinKey(Resources res, Keyboard.Row parent, int x, int y, XmlResourceParser parser) {
            super(res, parent, x, y, parser);
        }

        // Overriding this method so that we can reduce the target area for the key that closes the keyboard.
        @Override
        public boolean isInside(int x, int y) {
            return super.isInside(x, codes[0] == KEYCODE_CANCEL ? y - 10 : y);
        }
    }
}
