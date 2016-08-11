package name.agapoff.oresund;

import android.annotation.TargetApi;
import android.content.Context;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;

public class KeyboardViewFix extends KeyboardView {
    public static boolean inEditMode = true;

    @TargetApi(21)
    public KeyboardViewFix(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(new ContextWrapperFix(context, inEditMode), attrs, defStyleAttr, defStyleRes);
    }

    public KeyboardViewFix(Context context, AttributeSet attrs, int defStyleAttr) {
        super(new ContextWrapperFix(context, inEditMode), attrs, defStyleAttr);
    }

    public KeyboardViewFix(Context context, AttributeSet attrs) {
        super(new ContextWrapperFix(context, inEditMode), attrs);
    }

}
