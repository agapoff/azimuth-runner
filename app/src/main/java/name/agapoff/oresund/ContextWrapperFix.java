package name.agapoff.oresund;

import android.content.Context;
import android.content.ContextWrapper;

public class ContextWrapperFix extends ContextWrapper {
    private boolean editMode;

    public ContextWrapperFix(Context context, boolean editMode) {
        super(context);
        this.editMode = editMode;
    }

    public Object getSystemService(String name) {
        if (editMode && Context.AUDIO_SERVICE.equals(name)) {
            return null;
        }
        return super.getSystemService(name);
    }

}
