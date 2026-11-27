package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.HttpUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.ZipUtils;
import com.winlator.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * MusicSpot: one-place setup for running the Windows Spotify client with Spicetify
 * inside a Winlator container.
 *
 * Nothing from Spotify or Spicetify is bundled in the APK: the official installers are
 * downloaded at setup time from their official sources (download.scdn.co and the
 * spicetify GitHub releases).
 */
public class SpotifyFragment extends Fragment {
    private static final String PREF_CONTAINER_ID = "musicspot_container_id";
    private static final String CONTAINER_NAME = "Spotify (MusicSpot)";

    private static final String SPOTIFY_SETUP_URL = "https://download.scdn.co/SpotifySetup.exe";
    private static final String SPICETIFY_RELEASE_API = "https://api.github.com/repos/spicetify/cli/releases/latest";
    private static final String MARKETPLACE_URL = "https://github.com/spicetify/marketplace/releases/latest/download/marketplace.zip";

    private static final String WORK_DIR_DOS = "C:\\musicspot";
    private static final String SPOTIFY_EXE_DOS = "C:\\users\\" + RootFS.USER + "\\AppData\\Roaming\\Spotify\\Spotify.exe";
    private static final String CMD_EXE_DOS = "C:\\windows\\system32\\cmd.exe";

    private static final String THEME_NAME = "MusicSpotMobile";
    private static final String PORTRAIT_SCREEN_SIZE = "864x1920";
    private static final String SHORTCUT_PORTRAIT = "MusicSpot - Spotify (allo)";
    private static final String SHORTCUT_LANDSCAPE = "MusicSpot - Spotify (fekvo)";
    private static final String SHORTCUT_DEBUG = "MusicSpot - Spotify (naplo)";
    // OpenGL is not available to Wine on many phones (log: "Unable to activate OpenGL context"), so render in software.
    private static final String SAFE_ARGS = "--disable-gpu --disable-gpu-compositing --no-sandbox";
    private static final String DEBUG_ARGS = SAFE_ARGS + " --enable-logging=stderr";
    private static final String PREF_DEBUG = "musicspot_debug";

    private TextView statusView;
    private ContainerManager manager;
    private SharedPreferences preferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manager = new ContainerManager(requireContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)requireActivity()).getSupportActionBar().setTitle(R.string.spotify);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        int pad = dp(16);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        scrollView.addView(layout);

        TextView title = new TextView(context);
        title.setText("Spotify + Spicetify");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        layout.addView(title);

        TextView info = new TextView(context);
        info.setText("A Windowsos Spotify fut emulálva, rajta a Spicetify témákkal és bővítményekkel.\n" +
            "Lépésenként haladj: 1 → 2 → 3, utána a 4-es gombbal indítsd a Spotify-t.\n" +
            "Figyelem: az emuláció lassú lehet, és nem minden telefonon működik.");
        info.setPadding(0, dp(8), 0, dp(16));
        layout.addView(info);

        statusView = new TextView(context);
        statusView.setPadding(0, 0, 0, dp(16));
        statusView.setTypeface(Typeface.MONOSPACE);
        layout.addView(statusView);

        addButton(layout, "1. Előkészítés (letöltések)", v -> prepare());
        addButton(layout, "2. Spotify telepítése", v -> launchExe(new File(getWorkDir(), "SpotifySetup.exe")));
        addButton(layout, "3. Spicetify alkalmazása", v -> launchScript("apply.bat"));
        addButton(layout, "4. Spotify indítása – álló, telefonos", v -> launchShortcut(SHORTCUT_PORTRAIT));
        addButton(layout, "Spotify indítása – fekvő, asztali", v -> launchShortcut(SHORTCUT_LANDSCAPE));
        addButton(layout, "Spicetify újra (Spotify frissítés után)", v -> launchScript("reapply.bat"));
        addButton(layout, "Spicetify kikapcsolása (eredeti Spotify, hibakereséshez)", v -> launchScript("restore.bat"));
        addButton(layout, "Hibakereső indítás (napló készítése)", v -> launchDebug());
        addButton(layout, "Napló másolása a vágólapra", v -> copyLog());

        TextView help = new TextView(context);
        help.setText("\nTippek:\n" +
            "• A 2. lépésben jelentkezz be, majd zárd be a Spotify-t, mielőtt a 3. lépést futtatod.\n" +
            "• Témákat és bővítményeket a Spotify-on belül, a Marketplace menüpontban találsz.\n" +
            "• Álló módban a „MusicSpot Mobile” téma nagyobb, telefonos felületet ad; fekvő módban a megszokott kinézet marad.\n" +
            "• Ha a Spotify fekete ablakkal indul, a Parancsikonok menüben a „MusicSpot - Spotify” parancsikon beállításainál " +
            "add hozzá az indítási paraméterekhez: --disable-gpu");
        layout.addView(help);

        refreshStatus();
        return scrollView;
    }

    private void addButton(LinearLayout layout, String text, View.OnClickListener listener) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        layout.addView(button, params);
    }

    private int dp(int value) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    // ---- container & paths ------------------------------------------------------------------

    @Nullable
    private Container getSpotifyContainer() {
        int id = preferences.getInt(PREF_CONTAINER_ID, 0);
        return id > 0 ? manager.getContainerById(id) : null;
    }

    private File getDriveC(Container container) {
        return new File(container.getRootDir(), ".wine/drive_c");
    }

    @Nullable
    private File getWorkDir() {
        Container container = getSpotifyContainer();
        return container != null ? new File(getDriveC(container), "musicspot") : null;
    }

    private void refreshStatus() {
        if (statusView == null) return;
        Container container = getSpotifyContainer();
        StringBuilder sb = new StringBuilder();
        if (container == null) {
            sb.append("Konténer:  nincs még létrehozva\n");
        }
        else {
            File workDir = getWorkDir();
            File driveC = getDriveC(container);
            sb.append("Konténer:  ").append(container.getName()).append('\n');
            sb.append("Letöltések: ").append(new File(workDir, "spicetify/spicetify.exe").isFile() && new File(workDir, "SpotifySetup.exe").isFile() ? "kész" : "hiányzik").append('\n');
            sb.append("Spotify:   ").append(new File(driveC, "users/" + RootFS.USER + "/AppData/Roaming/Spotify/Spotify.exe").isFile() ? "telepítve" : "nincs telepítve").append('\n');
        }
        statusView.setText(sb.toString());
    }

    // ---- step 1: prepare ----------------------------------------------------------------------

    private void prepare() {
        final Activity activity = requireActivity();
        Container container = getSpotifyContainer();
        if (container != null) {
            downloadAll(activity, container);
            return;
        }

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.creating_container);
        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            // Spotify needs the COM (RpcSs) and BITS services, which the default "Essential" mode disables.
            data.put("startupSelection", Container.STARTUP_SELECTION_NORMAL);
            manager.createContainerAsync(data, (newContainer) -> {
                preloaderDialog.close();
                if (newContainer == null) {
                    toast("Nem sikerült létrehozni a konténert.");
                    return;
                }
                preferences.edit().putInt(PREF_CONTAINER_ID, newContainer.id).apply();
                refreshStatus();
                downloadAll(activity, newContainer);
            });
        }
        catch (JSONException e) {
            preloaderDialog.close();
        }
    }

    private void downloadAll(final Activity activity, final Container container) {
        final File workDir = new File(getDriveC(container), "musicspot");
        workDir.mkdirs();
        writeScripts(workDir);
        installTheme(container);
        createShortcuts(container);

        final File spotifySetup = new File(workDir, "SpotifySetup.exe");
        final File spicetifyZip = new File(workDir, "spicetify.zip");
        final File marketplaceZip = new File(workDir, "marketplace.zip");

        step(activity, SPOTIFY_SETUP_URL, spotifySetup, "Spotify telepítő", () ->
            findSpicetifyUrl(activity, (spicetifyUrl) ->
                step(activity, spicetifyUrl, spicetifyZip, "Spicetify", () ->
                    step(activity, MARKETPLACE_URL, marketplaceZip, "Spicetify Marketplace", () ->
                        extractAll(activity, container, workDir, spicetifyZip, marketplaceZip)))));
    }

    private void step(Activity activity, String url, File destination, String label, Runnable next) {
        if (destination.isFile() && destination.length() > 0) {
            next.run();
            return;
        }
        File tmp = new File(destination.getPath() + ".part");
        HttpUtils.download(activity, url, tmp, (success) -> {
            if (success && tmp.renameTo(destination)) {
                next.run();
            }
            else {
                tmp.delete();
                toast("Letöltési hiba: " + label + ". Ellenőrizd az internetet, és próbáld újra.");
                refreshStatus();
            }
        });
    }

    private void findSpicetifyUrl(final Activity activity, final Callback<String> callback) {
        HttpUtils.download(SPICETIFY_RELEASE_API, (json) -> activity.runOnUiThread(() -> {
            String url = null;
            try {
                if (json != null) {
                    JSONArray assets = new JSONObject(json).getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.getString("name");
                        if (name.endsWith("-windows-x64.zip")) {
                            url = asset.getString("browser_download_url");
                            break;
                        }
                    }
                }
            }
            catch (JSONException e) {}

            if (url != null) {
                callback.call(url);
            }
            else toast("Nem találom a Spicetify legújabb verzióját. Próbáld újra később.");
        }));
    }

    private void extractAll(Activity activity, Container container, File workDir, File spicetifyZip, File marketplaceZip) {
        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.loading);
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok;
            File spicetifyDir = new File(workDir, "spicetify");
            FileUtils.delete(spicetifyDir);
            spicetifyDir.mkdirs();
            ok = ZipUtils.extract(spicetifyZip, spicetifyDir);

            File customAppsDir = new File(getDriveC(container), "users/" + RootFS.USER + "/AppData/Roaming/spicetify/CustomApps");
            File marketplaceDir = new File(customAppsDir, "marketplace");
            File tmpDir = new File(customAppsDir, ".marketplace-tmp");
            FileUtils.delete(marketplaceDir);
            FileUtils.delete(tmpDir);
            tmpDir.mkdirs();
            if (ok && ZipUtils.extract(marketplaceZip, tmpDir)) {
                // The archive contains a single "marketplace-dist" folder.
                File dist = new File(tmpDir, "marketplace-dist");
                ok = (dist.isDirectory() ? dist : tmpDir).renameTo(marketplaceDir);
                FileUtils.delete(tmpDir);
            }
            else ok = false;

            final boolean success = ok;
            activity.runOnUiThread(() -> {
                preloaderDialog.close();
                refreshStatus();
                toast(success ? "Előkészítés kész! Jöhet a 2. lépés." : "Kicsomagolási hiba. Futtasd újra az 1. lépést.");
                if (!success) {
                    spicetifyZip.delete();
                    marketplaceZip.delete();
                }
            });
        });
    }

    private void writeScripts(File workDir) {
        String header = "@echo off\r\n" +
            "cd /d " + WORK_DIR_DOS + "\\spicetify\r\n" +
            "echo MusicSpot - Spicetify\r\n" +
            "echo.\r\n" +
            "tasklist | find /i \"Spotify.exe\" >nul && taskkill /f /im Spotify.exe >nul\r\n";
        String footer = "echo.\r\n" +
            "echo Kesz. Most mar elindithatod a Spotify-t (4. gomb).\r\n" +
            "pause\r\n";

        FileUtils.writeString(new File(workDir, "apply.bat"), header +
            "spicetify.exe --bypass-admin config custom_apps marketplace\r\n" +
            "spicetify.exe --bypass-admin config current_theme " + THEME_NAME + " color_scheme Mobile inject_css 1 replace_colors 1\r\n" +
            "spicetify.exe --bypass-admin backup apply\r\n" +
            footer);

        FileUtils.writeString(new File(workDir, "reapply.bat"), header +
            "spicetify.exe --bypass-admin restore backup apply\r\n" +
            footer);

        FileUtils.writeString(new File(workDir, "restore.bat"), header +
            "spicetify.exe --bypass-admin restore\r\n" +
            "echo.\r\n" +
            "echo A Spotify most eredeti, Spicetify nelkul fut. Visszakapcsolas: 3. gomb.\r\n" +
            "pause\r\n");
    }

    /** Settings Spotify needs; applied to existing containers too, so older installs get fixed. */
    private void ensureContainerSettings(Container container) {
        if (container.getStartupSelection() != Container.STARTUP_SELECTION_NORMAL) {
            container.setStartupSelection(Container.STARTUP_SELECTION_NORMAL);
            container.saveData();
        }
    }

    /** Older shortcuts were created without arguments; give them the safe software-rendering ones. */
    private void ensureSafeArgs(File shortcutFile, Container container) {
        if (!shortcutFile.isFile()) return;
        com.winlator.container.Shortcut shortcut = new com.winlator.container.Shortcut(container, shortcutFile);
        String args = shortcut.getExtra("execArgs");
        if (!args.contains("--disable-gpu")) {
            shortcut.putExtra("execArgs", (SAFE_ARGS + " " + args).trim());
            shortcut.saveData();
        }
    }

    private void createShortcuts(Container container) {
        File desktopDir = new File(container.getUserDir(), "Desktop");
        desktopDir.mkdirs();
        ensureSafeArgs(new File(desktopDir, SHORTCUT_PORTRAIT + ".desktop"), container);
        ensureSafeArgs(new File(desktopDir, SHORTCUT_LANDSCAPE + ".desktop"), container);
        writeShortcut(new File(desktopDir, SHORTCUT_PORTRAIT + ".desktop"), SPOTIFY_EXE_DOS,
            "screenSize=" + PORTRAIT_SCREEN_SIZE + "\norientation=portrait\nexecArgs=" + SAFE_ARGS + "\n");
        writeShortcut(new File(desktopDir, SHORTCUT_LANDSCAPE + ".desktop"), SPOTIFY_EXE_DOS, "execArgs=" + SAFE_ARGS + "\n");
        writeShortcut(new File(desktopDir, SHORTCUT_DEBUG + ".desktop"), SPOTIFY_EXE_DOS, "execArgs=" + DEBUG_ARGS + "\n");
        writeShortcut(new File(desktopDir, "MusicSpot - Spicetify alkalmazasa.desktop"), CMD_EXE_DOS, "execArgs=/c " + WORK_DIR_DOS + "\\apply.bat\n");
        writeShortcut(new File(desktopDir, "MusicSpot - Spicetify ujra.desktop"), CMD_EXE_DOS, "execArgs=/c " + WORK_DIR_DOS + "\\reapply.bat\n");
        writeShortcut(new File(desktopDir, "MusicSpot - Spicetify kikapcsolasa.desktop"), CMD_EXE_DOS, "execArgs=/c " + WORK_DIR_DOS + "\\restore.bat\n");
    }

    private void installTheme(Container container) {
        File themeDir = new File(getDriveC(container), "users/" + RootFS.USER + "/AppData/Roaming/spicetify/Themes/" + THEME_NAME);
        FileUtils.delete(themeDir);
        FileUtils.copy(requireContext(), "musicspot/" + THEME_NAME, themeDir);
    }

    /** extraData: newline-separated key=value lines for the [Extra Data] section, or "". */
    private static void writeShortcut(File file, String dosPath, String extraData) {
        if (file.isFile()) return; // keep user customisations
        // Wine's .desktop format escapes each backslash as four backslashes (see Shortcut/StringUtils.unescapeDOSPath).
        String escaped = dosPath.replace("\\", "\\\\\\\\");
        String content = "[Desktop Entry]\n" +
            "Name=" + FileUtils.getBasename(file.getName()) + "\n" +
            "Exec=env WINEPREFIX=\"/home/" + RootFS.USER + "/.wine\" wine " + escaped + "\n" +
            "Type=Application\n" +
            "StartupNotify=true\n";
        if (!extraData.isEmpty()) content += "\n[Extra Data]\n" + extraData;
        FileUtils.writeString(file, content);
    }

    // ---- launching -----------------------------------------------------------------------------

    private boolean ensureReady() {
        if (getSpotifyContainer() == null) {
            toast("Előbb futtasd az 1. lépést (Előkészítés).");
            return false;
        }
        return true;
    }

    private void launchExe(File exe) {
        if (!ensureReady()) return;
        if (exe == null || !exe.isFile()) {
            toast("Hiányzik: " + (exe != null ? exe.getName() : "?") + ". Futtasd az 1. lépést.");
            return;
        }
        Container container = getSpotifyContainer();
        ensureContainerSettings(container);
        Intent intent = new Intent(requireActivity(), XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getPath());
        startActivity(intent);
    }

    private void launchScript(String name) {
        if (!ensureReady()) return;
        File workDir = getWorkDir();
        if (!new File(workDir, "spicetify/spicetify.exe").isFile()) {
            toast("A Spicetify még nincs letöltve. Futtasd az 1. lépést.");
            return;
        }
        Container container = getSpotifyContainer();
        installTheme(container);
        writeScripts(workDir); // older installs may lack newer scripts
        String shortcut;
        switch (name) {
            case "apply.bat": shortcut = "MusicSpot - Spicetify alkalmazasa"; break;
            case "restore.bat": shortcut = "MusicSpot - Spicetify kikapcsolasa"; break;
            default: shortcut = "MusicSpot - Spicetify ujra"; break;
        }
        launchShortcut(shortcut);
    }

    private File getLogFile() {
        return new File(requireContext().getFilesDir(), "musicspot-log.txt");
    }

    private void setDebugMode(boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit();
        if (enabled) {
            editor.putBoolean("enable_wine_debug", true)
                .putString("wine_debug_channels", "err")
                .putBoolean("save_logs_to_file", true)
                .putString("log_file", getLogFile().getPath())
                .putInt("box64_logs", 1)
                .putBoolean(PREF_DEBUG, true);
        }
        else if (preferences.getBoolean(PREF_DEBUG, false)) {
            editor.putBoolean("enable_wine_debug", false)
                .putBoolean("save_logs_to_file", false)
                .remove("log_file")
                .putInt("box64_logs", 0)
                .putBoolean(PREF_DEBUG, false);
        }
        editor.apply();
    }

    private void launchDebug() {
        if (!ensureReady()) return;
        setDebugMode(true);
        toast("Hibakereső mód: várj 3–5 percet, majd lépj ki (Vissza → Exit), és nyomd meg a „Napló másolása” gombot.");
        launchShortcut(SHORTCUT_DEBUG, true);
    }

    private void copyLog() {
        File logFile = getLogFile();
        if (!logFile.isFile() || logFile.length() == 0) {
            toast("Még nincs napló. Előbb futtasd a „Hibakereső indítás” gombot.");
            return;
        }
        java.util.ArrayList<String> lines = FileUtils.readLines(logFile, true);
        java.util.ArrayList<String> important = new java.util.ArrayList<>();
        for (String line : lines) {
            String l = line.toLowerCase(java.util.Locale.ENGLISH);
            if (l.contains("err:") || l.contains(":error:") || l.contains("fatal") || l.contains("exception") ||
                l.contains("segfault") || l.contains("sigsegv") || l.contains("missing") || l.contains("not found") ||
                l.contains("unimplemented") || l.contains("failed")) {
                important.add(line);
            }
        }
        java.util.List<String> selected = important.isEmpty() ? lines : important;
        int from = Math.max(0, selected.size() - 120);
        StringBuilder sb = new StringBuilder();
        sb.append("MusicSpot napló (").append(lines.size()).append(" sor, ").append(important.size()).append(" fontos)\n");
        for (int i = from; i < selected.size(); i++) sb.append(selected.get(i)).append('\n');
        if (!important.isEmpty()) {
            sb.append("--- utolsó 30 sor ---\n");
            for (int i = Math.max(0, lines.size() - 30); i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
        }

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MusicSpot napló", sb.toString()));
        toast("A napló a vágólapon van – illeszd be a chatbe.");
    }

    private void launchShortcut(String name) {
        launchShortcut(name, false);
    }

    private void launchShortcut(String name, boolean debug) {
        if (!ensureReady()) return;
        if (!debug) setDebugMode(false);
        Container container = getSpotifyContainer();
        ensureContainerSettings(container);
        createShortcuts(container);
        File shortcut = new File(new File(container.getUserDir(), "Desktop"), name + ".desktop");
        if (name.startsWith("MusicSpot - Spotify") &&
            !new File(getDriveC(container), "users/" + RootFS.USER + "/AppData/Roaming/Spotify/Spotify.exe").isFile()) {
            toast("A Spotify még nincs telepítve. Futtasd a 2. lépést.");
            return;
        }
        Intent intent = new Intent(requireActivity(), XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcut.getPath());
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void toast(String text) {
        Context context = getContext();
        if (context != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
