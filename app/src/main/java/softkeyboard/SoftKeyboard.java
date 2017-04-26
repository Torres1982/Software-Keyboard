package softkeyboard;

import android.app.Dialog;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.IBinder;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.example.android.softkeyboard.R;

public class SoftKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    static final boolean PROCESS_HARD_KEYS = true;
    private boolean capsLock;
    boolean predictionOn;

    private String wordSeparators;
    private StringBuilder stringBuilder = new StringBuilder();

    private int lastDisplayWidth;
    private long lastShiftTime;
    private long metaState;

    // Different Keyboards
    private LatinKeyboard keyboardNumbers;
    private LatinKeyboard keyboardLetters;
    private LatinKeyboard currentKeyboard;

    private LatinKeyboardView inputView;
    private InputMethodManager inputMethodManager;

    /**
     * Main initialization of the input method component
     */
    @Override public void onCreate() {
        super.onCreate();
        inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        wordSeparators = getResources().getString(R.string.word_separators);
    }

    /**
     * This is the point where you can do all of your UI initialization.
     * It is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        if (keyboardLetters != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == lastDisplayWidth) return;
            lastDisplayWidth = displayWidth;
        }
        keyboardLetters = new LatinKeyboard(this, R.xml.qwerty);
        keyboardNumbers = new LatinKeyboard(this, R.xml.symbols);
        //mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
    }

    // Set and display the letter keyboard first time the keyboard is called
    @Override public View onCreateInputView() {
        inputView = (LatinKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        inputView.setOnKeyboardActionListener(this);
        inputView.setPreviewEnabled(false);
        setLatinKeyboard(keyboardLetters);
        return inputView;
    }

    private void setLatinKeyboard(LatinKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey = inputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());
        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);
        inputView.setKeyboard(nextKeyboard);
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        stringBuilder.setLength(0);

        if (!restarting) {
            // Clear shift states.
            metaState = 0;
        }

        // Initialize the state based on the type of text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard
                currentKeyboard = keyboardNumbers;
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                currentKeyboard = keyboardNumbers;
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the normal alphabetic keyboard
                currentKeyboard = keyboardLetters;

                // We also want to look at the current state of the editor to decide
                // whether our alphabetic keyboard should start out shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic keyboard with no special features.
                currentKeyboard = keyboardLetters;
                updateShiftKeyState(attribute);
        }
    }

    // User has finished editing the fields so the state of the input field can be reset
    @Override public void onFinishInput() {
        super.onFinishInput();

        // Clear current composing text and candidates.
        stringBuilder.setLength(0);

        currentKeyboard = keyboardLetters;

        if (inputView != null) {
            inputView.closing();
        }
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        setLatinKeyboard(currentKeyboard);
        inputView.closing();
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (stringBuilder.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            stringBuilder.setLength(0);
            //updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an InputConnection
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        metaState = MetaKeyKeyListener.handleKeyDown(metaState, keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(metaState));
        metaState = MetaKeyKeyListener.adjustMetaAfterKeypress(metaState);
        InputConnection ic = getCurrentInputConnection();

        if (c == 0 || ic == null) {
            return false;
        }

        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (stringBuilder.length() > 0) {
            char accent = stringBuilder.charAt(stringBuilder.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                stringBuilder.setLength(stringBuilder.length()-1);
            }
        }

        onKey(c, null);

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && inputView != null) {
                    if (inputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (stringBuilder.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            default:
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (predictionOn) {
                metaState = MetaKeyKeyListener.handleKeyUp(metaState, keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    // Helper function to commit any text being composed in to the editor.
    private void commitTyped(InputConnection inputConnection) {
        if (stringBuilder.length() > 0) {
            inputConnection.commitText(stringBuilder, stringBuilder.length());
            stringBuilder.setLength(0);
        }
    }

    // Helper to update the shift state of our keyboard based on the initial editor state
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && inputView != null && keyboardLetters == inputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            inputView.setShifted(capsLock || caps != 0);
        }
    }

    // Helper to determine if a given character code is alphabetic.
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        }
        else {
            return false;
        }
    }

    // Helper to send a key down / key up pair to the current editor.
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    // Helper to send a character to the editor as raw key events.
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                }
                else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of Keyboard View Listener
    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (stringBuilder.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
        else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        }
        else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        }
        // Switching between keyboards
        else if (primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch();
            return;
        }
        else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && inputView != null) {
            Keyboard current = inputView.getKeyboard();
            if (current == keyboardNumbers) {
                setLatinKeyboard(keyboardLetters);
            }
            else {
                setLatinKeyboard(keyboardNumbers);
                keyboardNumbers.setShifted(false);
            }
        }
        else {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();

        if (ic == null) return;

        ic.beginBatchEdit();

        if (stringBuilder.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    // Helper function to support Delete key
    private void handleBackspace() {
        final int length = stringBuilder.length();
        if (length > 1) {
            stringBuilder.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(stringBuilder, 1);
        }
        else if (length > 0) {
            stringBuilder.setLength(0);
            getCurrentInputConnection().commitText("", 0);
        }
        else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    // Helper function to support Shift key
    private void handleShift() {
        if (inputView == null) {
            return;
        }

        Keyboard currentKeyboard = inputView.getKeyboard();

        if (keyboardLetters == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            inputView.setShifted(capsLock || !inputView.isShifted());
        }
    }

    // Helper function to support Letter characters
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (inputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && predictionOn) {
            stringBuilder.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(stringBuilder, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
        else {
            getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        inputView.closing();
    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    // Change between different keyboards - (input methods)
    private void handleLanguageSwitch() {
        inputMethodManager.switchToNextInputMethod(getToken(), false);
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (lastShiftTime + 800 > now) {
            capsLock = !capsLock;
            lastShiftTime = 0;
        }
        else {
            lastShiftTime = now;
        }
    }

    // Return all available word separators
    private String getWordSeparators() {
        return wordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void swipeRight() {}

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {}

    public void onPress(int primaryCode) {}

    public void onRelease(int primaryCode) {}
}
