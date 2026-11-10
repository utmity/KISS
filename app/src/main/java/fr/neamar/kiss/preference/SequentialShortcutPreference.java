package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.NameComparator;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.ui.SequentialShortcuts;

/** Reads the app list at click time, after providers have had time to load. */
public class SequentialShortcutPreference extends Preference {
    public SequentialShortcutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        List<Integer> keys = new ArrayList<>();
        for (int key = KeyEvent.KEYCODE_A; key <= KeyEvent.KEYCODE_Z; key++) keys.add(key);
        for (int key = KeyEvent.KEYCODE_0; key <= KeyEvent.KEYCODE_9; key++) keys.add(key);
        CharSequence[] labels = new CharSequence[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            String id = getSharedPreferences().getString(SequentialShortcuts.BINDING + keys.get(i), "");
            Pojo app = id.isEmpty() ? null : KissApplication.getApplication(getContext()).getDataHandler().getPojo(id);
            String name = id.isEmpty() ? getContext().getString(R.string.sequence_unassigned)
                    : !(app instanceof AppPojo) || app.isDisabled()
                    ? getContext().getString(R.string.sequence_unavailable) : appLabel((AppPojo) app);
            labels[i] = keyLabel(keys.get(i)) + " — " + name;
        }
        new AlertDialog.Builder(getContext()).setTitle(R.string.sequence_assignments)
                .setItems(labels, (dialog, position) -> chooseApp(keys.get(position)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static String keyLabel(int key) {
        return KeyEvent.keyCodeToString(key).replace("KEYCODE_", "");
    }

    private String appLabel(AppPojo app) {
        return app.getName() + (app.userHandle.isCurrentUser() ? ""
                : " (" + getContext().getString(R.string.sequence_other_profile) + ")");
    }

    private void chooseApp(int key) {
        List<AppPojo> available = KissApplication.getApplication(getContext()).getDataHandler().getApplications();
        List<AppPojo> apps = available == null ? new ArrayList<>() : new ArrayList<>(available);
        apps.removeIf(AppPojo::isDisabled);
        Collections.sort(apps, new NameComparator());
        CharSequence[] labels = new CharSequence[apps.size() + 1];
        labels[0] = getContext().getString(R.string.sequence_unassigned);
        for (int i = 0; i < apps.size(); i++) {
            AppPojo app = apps.get(i);
            labels[i + 1] = appLabel(app);
        }
        String setting = SequentialShortcuts.BINDING + key;
        String current = getSharedPreferences().getString(setting, "");
        int selected = current.isEmpty() ? 0 : -1;
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).id.equals(current)) selected = i + 1;
        new AlertDialog.Builder(getContext()).setTitle(keyLabel(key))
                .setSingleChoiceItems(labels, selected, (dialog, position) -> {
                    if (position == 0) getSharedPreferences().edit().remove(setting).apply();
                    else getSharedPreferences().edit().putString(setting, apps.get(position - 1).id).apply();
                    dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
