package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Uydu ve PlayStation (HDMI 2) kutucukları. */
final class Sources {
    private Sources() {}

    static final String LIVE_TV_PKG = "org.droidtv.playtv";

    /** Lina ayarları: TV'ye özel config.json içindeki "lina_*" anahtarları. */
    static org.json.JSONObject linaConfig(Context ctx) {
        try {
            java.io.File dir = ctx.getExternalFilesDir(null);
            java.io.File f = dir != null ? new java.io.File(dir, "config.json") : null;
            if (f == null || !f.isFile()) return new org.json.JSONObject();
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            return new org.json.JSONObject(new String(b, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    static List<AppAdapter.Entry> build(Context ctx) {
        List<AppAdapter.Entry> out = new ArrayList<>();
        org.json.JSONObject lc = linaConfig(ctx);
        String livePkg = lc.optString("live_tv_package", LIVE_TV_PKG);

        // Uydu: Philips'in canlı yayın uygulaması
        Intent sat = livePkg.isEmpty() ? null : ctx.getPackageManager().getLeanbackLaunchIntentForPackage(livePkg);
        if (sat == null && !livePkg.isEmpty()) sat = ctx.getPackageManager().getLaunchIntentForPackage(livePkg);
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (sat == null && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_TUNER) {
                    sat = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(info.getId()));
                    break;
                }
            }
        }
        if (sat != null && lc.optBoolean("lina_uydu", true)) {
            AppAdapter.Entry e = new AppAdapter.Entry(AppAdapter.Entry.SOURCE, lc.optString("lina_uydu_label", "Uydu"),
                    new ComponentName(ctx, "source.uydu"),
                    ctx.getDrawable(R.drawable.tile_uydu), null, null, 0xFF1D4ED8);
            e.intent = sat;
            out.add(e);
        }

        // PlayStation: HDMI 2
        String ps = lc.optBoolean("lina_playstation", false) ? playStationInput(ctx, tim) : null;
        if (ps != null) {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(ps);
            AppAdapter.Entry e = new AppAdapter.Entry(AppAdapter.Entry.SOURCE, "PlayStation",
                    new ComponentName(ctx, "source.playstation"),
                    ctx.getDrawable(R.drawable.tile_playstation), null, null, 0xFF0046C8);
            e.intent = new Intent(Intent.ACTION_VIEW, uri);
            out.add(e);
        }
        // Google Arama (katniss)
        Intent g = new Intent("android.search.action.GLOBAL_SEARCH");
        if (lc.optBoolean("lina_google", true) && ctx.getPackageManager().resolveActivity(g, 0) != null) {
            AppAdapter.Entry e = new AppAdapter.Entry(AppAdapter.Entry.SOURCE, "Google Arama",
                    new ComponentName(ctx, "source.google"),
                    ctx.getDrawable(R.drawable.tile_google), null, null, 0xFF4285F4);
            e.intent = g;
            out.add(e);
        }
        return out;
    }

    private static String label(Context ctx, TvInputInfo info) {
        StringBuilder sb = new StringBuilder();
        try {
            Object c = TvInputInfo.class.getMethod("loadCustomLabel", Context.class).invoke(info, ctx);
            if (c != null) sb.append(c).append(' ');
        } catch (Exception ignored) {}
        try {
            CharSequence l = info.loadLabel(ctx);
            if (l != null) sb.append(l).append(' ');
        } catch (Exception ignored) {}
        try {
            Object hd = TvInputInfo.class.getMethod("getHdmiDeviceInfo").invoke(info);
            if (hd != null) sb.append(hd.getClass().getMethod("getDisplayName").invoke(hd));
        } catch (Exception ignored) {}
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Önce adında PlayStation/PS geçen girişi, yoksa "HDMI 2" etiketli girişi, yoksa ikinci HDMI'yi seçer. */
    private static String playStationInput(Context ctx, TvInputManager tim) {
        if (tim == null) return null;
        String byName = null, byHdmi2 = null;
        List<TvInputInfo> hdmi = new ArrayList<>();
        for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            String l = label(ctx, info);
            if (byName == null && (l.contains("playstation") || l.contains("ps5") || l.contains("ps4"))) byName = info.getId();
            if (byHdmi2 == null && info.getParentId() == null && l.replace(" ", "").contains("hdmi2")) byHdmi2 = info.getId();
            if (info.getParentId() == null) hdmi.add(info);
        }
        if (byName != null) return byName;
        if (byHdmi2 != null) return byHdmi2;
        if (hdmi.size() >= 2) {
            hdmi.sort((a, b) -> a.getId().compareTo(b.getId()));
            return hdmi.get(1).getId();
        }
        return null;
    }
}
