package fr.neamar.kiss.preference;

import android.content.Context;
import androidx.preference.ListPreference;

/** Reuses KISS app/profile IDs and adds an explicit unassign entry. */
public class KeyboardAppPreference extends LaunchPojoSelectPreference {
    public KeyboardAppPreference(Context context) {
        super(context);
        CharSequence[] oldEntries = getEntries();
        CharSequence[] oldValues = getEntryValues();
        CharSequence[] entries = new CharSequence[oldEntries.length + 1];
        CharSequence[] values = new CharSequence[oldValues.length + 1];
        entries[0] = context.getString(fr.neamar.kiss.R.string.keyboard_shortcuts_none);
        values[0] = "";
        System.arraycopy(oldEntries, 0, entries, 1, oldEntries.length);
        System.arraycopy(oldValues, 0, values, 1, oldValues.length);
        setEntries(entries);
        setEntryValues(values);
        setEnabled(true);
        setDefaultValue("");
        setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }
}
