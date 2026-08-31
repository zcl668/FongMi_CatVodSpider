package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HellYell extends Spider {

    private static final String HOST = "https://radio.hellyell.com";
    private static final int TIMEOUT = 10000;
    private static final Map<String, String> HEADERS = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    }};

    private final List<Map<String, String>> classes = new ArrayList<Map<String, String>>() {{
        add(new HashMap<String, String>() {{ put("type_id", "foreign-music"); put("type_name", "国际音乐台"); }});
        add(new HashMap<String, String>() {{ put("type_id", "chinese-music"); put("type_name", "中文音乐台"); }});
        add(new HashMap<String, String>() {{ put("type_id", "news-comprehensive"); put("type_name", "新闻综合台"); }});
        add(new HashMap<String, String>() {{ put("type_id", "huaijiu-musiclist"); put("type_name", "怀旧电台"); }});
        add(new HashMap<String, String>() {{ put("type_id", "qiche-musiclist"); put("type_name", "汽车电台"); }});
    }};

    @Override
    public void init(Context context, String extend) throws Exception {
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONArray classArray = new JSONArray();
            for (Map<String, String> c : classes) {
                JSONObject item = new JSONObject();
                item.put("type_id", c.get("type_id"));
                item.put("type_name", c.get("type_name"));
                classArray.put(item);
            }
            JSONObject result = new JSONObject();
            result.put("class", classArray);
            return result.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public String homeVideoContent() {
        return "{\"list\": []}";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String classId = (tid == null || tid.isEmpty()) ? "chinese-music" : tid;
            String url = HOST + "/" + classId + ".json";
            String resp = fetch(url, HEADERS, null);
            if (resp == null) {
                return "{\"page\":1,\"pagecount\":1,\"limit\":100,\"total\":0,\"list\":[]}";
            }

            JSONArray stations = new JSONArray(resp);
            JSONArray list = new JSONArray();
            for (int i = 0; i < stations.length(); i++) {
                JSONObject station = stations.getJSONObject(i);
                String stationName = station.optString("name", "");
                String tag = station.optString("tag", "");
                if ("huaijiu-musiclist".equals(classId) && "HellYell怀旧电台".equals(stationName)) {
                    continue;
                }
                if ("qiche-musiclist".equals(classId) && "HellYell汽车电台".equals(stationName)) {
                    continue;
                }
                String remark = "在线电台";
                if (!tag.isEmpty() && !"推荐".equals(tag)) {
                    remark = tag;
                } else if (station.optBoolean("recommended", false) && !"推荐".equals(tag)) {
                    remark = "精选";
                }
                JSONObject item = new JSONObject();
                item.put("vod_id", station.optString("url", ""));
                item.put("vod_name", stationName);
                item.put("vod_pic", HOST + "/favicon.ico");
                item.put("vod_remarks", remark);
                item.put("style", new JSONObject().put("type", "list"));
                list.put(item);
            }
            JSONObject result = new JSONObject();
            result.put("page", 1);
            result.put("pagecount", 1);
            result.put("limit", 100);
            result.put("total", list.length());
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            return "{\"page\":1,\"pagecount\":1,\"limit\":100,\"total\":0,\"list\":[]}";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String playUrl = "";
            String name = "电台";

            if (ids != null && !ids.isEmpty()) {
                playUrl = ids.get(0);
            }

            if (("电台".equals(name) || name.isEmpty()) && !playUrl.isEmpty()) {
                try {
                    String[] allClasses = {"foreign-music", "chinese-music", "news-comprehensive", "huaijiu-musiclist", "qiche-musiclist"};
                    for (String c : allClasses) {
                        String url = HOST + "/" + c + ".json";
                        String resp = fetch(url, HEADERS, null);
                        if (resp == null) continue;
                        JSONArray stations = new JSONArray(resp);
                        for (int i = 0; i < stations.length(); i++) {
                            JSONObject s = stations.getJSONObject(i);
                            if (playUrl.equals(s.optString("url", ""))) {
                                name = s.optString("name", "电台");
                                break;
                            }
                        }
                        if (!"电台".equals(name) && !name.isEmpty()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                if ("电台".equals(name) || name.isEmpty()) {
                    name = "HellYell电台";
                }
            }

            JSONObject detail = new JSONObject();
            detail.put("vod_id", playUrl);
            detail.put("vod_name", name);
            detail.put("vod_pic", HOST + "/favicon.ico");
            detail.put("vod_play_from", "木头的木,平凡的凡!");
            detail.put("vod_play_url", "直播$" + playUrl);

            JSONArray list = new JSONArray();
            list.put(detail);
            return new JSONObject().put("list", list).toString();
        } catch (Exception e) {
            return "{\"list\": []}";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl = id;
            Map<String, String> playHeaders = new HashMap<>(HEADERS);

            // 网易云音乐外链：解析 302 重定向到真实地址，并设置正确的 Referer
            if (playUrl != null && playUrl.contains("music.163.com")) {
                playHeaders.put("Referer", "https://music.163.com/");
                String realUrl = resolveRedirect(playUrl, playHeaders);
                if (realUrl != null && !realUrl.isEmpty()) {
                    playUrl = realUrl;
                }
            }

            // 补全浏览器常见请求头，提高兼容性
            playHeaders.put("Accept", "*/*");
            playHeaders.put("Accept-Encoding", "identity;q=1, *;q=0");
            playHeaders.put("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            playHeaders.put("Connection", "keep-alive");

            JSONObject headerObj = new JSONObject();
            for (Map.Entry<String, String> entry : playHeaders.entrySet()) {
                headerObj.put(entry.getKey(), entry.getValue());
            }

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", playUrl);
            result.put("header", headerObj.toString());
            result.put("jx", 0);
            return result.toString();
        } catch (Exception e) {
            try {
                return new JSONObject()
                        .put("parse", 0)
                        .put("url", id)
                        .put("jx", 0)
                        .toString();
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    public String searchContent(String key, boolean quick) {
        try {
            return new JSONObject().put("list", new JSONArray()).toString();
        } catch (Exception e) {
            return "{\"list\": []}";
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        return false;
    }

    @Override
    public boolean manualVideoCheck() {
        return false;
    }

    @Override
    public void destroy() {
    }

    /**
     * 跟随 302/301 重定向，返回最终真实 URL
     */
    private String resolveRedirect(String urlStr, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            conn.setDoInput(true);

            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    if (location.startsWith("http")) {
                        return location;
                    } else {
                        return url.getProtocol() + "://" + url.getHost() + location;
                    }
                }
            }
            return urlStr;
        } catch (Exception e) {
            return urlStr;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String fetch(String url, Map<String, String> headers, String body) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setDoInput(true);

            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
