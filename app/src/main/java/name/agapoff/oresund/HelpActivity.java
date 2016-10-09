package name.agapoff.oresund;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView tvVersion = (TextView) findViewById(R.id.help_version);
        tvVersion.setText(getVersionInfo());
    }

    private String getVersionInfo() {
        String strVersion;

        PackageInfo packageInfo;
        try {
            packageInfo = getApplicationContext()
                    .getPackageManager()
                    .getPackageInfo(
                            getApplicationContext().getPackageName(),
                            0
                    );
            strVersion = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            strVersion = "n/a";
        }

        return strVersion;
    }
}
