package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class QtFm_mufan extends Spider {

    private static final String HOST = "https://www.qtfm.cn";
    private static final int TIMEOUT = 15000;
    private static final int LIMIT = 12;

    private HashMap<String, String> headers;
    private HashMap<String, String> mHeaders;
    private List<String[]> mufanStations;

    private static final String[] CLASS_NAME = {
        "广东","木凡喜爱的广播","车天车地车世界","浙江","北京","天津","河北","上海","山西","内蒙古",
        "辽宁","吉林","黑龙江","江苏","安徽","福建","江西","山东",
        "河南","湖北","湖南","广西","海南","重庆","四川","贵州",
        "云南","陕西","甘肃","宁夏","新疆","西藏","青海",
        "资讯","音乐","交通","经济","文艺","都市","体育","双语",
        "综合","生活","旅游","曲艺","方言"
    };

    private static final String[] CLASS_URL = {
        "217","mufan","channel_179056","99","3","5","7","83","19","31","44","59","69","85",
        "111","129","139","151","169","187","202","239","254","257",
        "259","281","291","316","327","351","357","308","342",
        "433","442","429","439","432","441","430","431","440",
        "438","435","436","434"
    };

    private static class TrustAllManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }

    private static class TrustAllHostnameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) { return true; }
    }

    @Override
    public void init(Context ctx, String extend) throws Exception {
        headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", "https://www.qtfm.cn/");

        mHeaders = new HashMap<>();
        mHeaders.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15");
        mHeaders.put("Referer", "https://m.qtfm.cn/");
        mHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        mHeaders.put("Accept-Language", "zh-CN,zh;q=0.9");

        mufanStations = new ArrayList<>();
        if (extend != null && !extend.trim().isEmpty()) {
            String ext = extend.trim();
            if (ext.startsWith("http://") || ext.startsWith("https://")) {
                String content = fetch(ext, false);
                parseMufanContent(content, ext.endsWith(".json"));
            } else if (ext.startsWith("file://")) {
                parseMufanFile(ext.replace("file://", ""));
            } else {
                parseMufanExtend(ext);
            }
        }
    }

    private void parseMufanContent(String content, boolean isJson) {
        if (content == null || content.trim().isEmpty()) return;
        if (isJson || content.trim().startsWith("[") || content.trim().startsWith("{")) {
            parseMufanExtend(content);
        } else {
            parseMufanTxt(content);
        }
    }

    private void parseMufanFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            parseMufanContent(sb.toString(), path.endsWith(".json"));
        } catch (Exception e) {
            System.out.println("[QtFm] read local file error: " + e.getMessage());
        }
    }

    private void parseMufanTxt(String content) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts;
            if (line.contains(",")) parts = line.split(",", 2);
            else if (line.contains("|")) parts = line.split("\\|", 2);
            else if (line.contains("=")) parts = line.split("=", 2);
            else continue;
            if (parts.length == 2) {
                String name = parts[0].trim();
                String url = parts[1].trim();
                if (!name.isEmpty() && !url.isEmpty()) {
                    mufanStations.add(new String[]{name, url});
                }
            }
        }
    }

    private void parseMufanExtend(String extend) {
        try {
            int idx = extend.indexOf("\"mufan\"");
            if (idx < 0) idx = extend.indexOf("'mufan'");
            if (idx < 0) {
                Pattern direct = Pattern.compile("\\[\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*\\]");
                Matcher dm = direct.matcher(extend);
                while (dm.find()) {
                    String name = dm.group(1).trim();
                    String url = dm.group(2).trim();
                    if (!name.isEmpty() && !url.isEmpty()) {
                        mufanStations.add(new String[]{name, url});
                    }
                }
                return;
            }

            int bracketStart = extend.indexOf('[', idx);
            if (bracketStart < 0) return;

            int bracketEnd = bracketStart + 1;
            int depth = 1;
            boolean inString = false;
            char stringChar = 0;

            for (int i = bracketEnd; i < extend.length(); i++) {
                char c = extend.charAt(i);
                if (inString) {
                    if (c == '\\') {
                        i++;
                    } else if (c == stringChar) {
                        inString = false;
                    }
                } else {
                    if (c == '"' || c == '\'') {
                        inString = true;
                        stringChar = c;
                    } else if (c == '[') {
                        depth++;
                    } else if (c == ']') {
                        depth--;
                        if (depth == 0) {
                            bracketEnd = i;
                            break;
                        }
                    }
                }
            }

            String arrContent = extend.substring(bracketStart + 1, bracketEnd);
            Pattern p = Pattern.compile("\\[\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*\\]");
            Matcher m = p.matcher(arrContent);
            while (m.find()) {
                String name = m.group(1).trim();
                String url = m.group(2).trim();
                if (!name.isEmpty() && !url.isEmpty()) {
                    mufanStations.add(new String[]{name, url});
                }
            }
        } catch (Exception e) {
            System.out.println("[QtFm] parseMufanExtend error: " + e.getMessage());
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        String pattern = "http((?!http).){26,}\\.(m3u8|mp4|flv|avi|mkv|wmv|mpg|mpeg|mov|ts|3gp|rm|rmvb|asf|m4a|mp3|wma)";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(url).find();
    }

    @Override
    public boolean manualVideoCheck() { return false; }

    @Override
    public void destroy() {}

    private String fetch(String urlStr, boolean useMobile) {
        Map<String, String> hdr = useMobile ? mHeaders : headers;
        HttpURLConnection conn = null;
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                URL url = new URL(urlStr);
                if (urlStr.startsWith("https://")) {
                    HttpsURLConnection httpsConn = (HttpsURLConnection) url.openConnection();
                    httpsConn.setSSLSocketFactory(getTrustAllSSLContext().getSocketFactory());
                    httpsConn.setHostnameVerifier(new TrustAllHostnameVerifier());
                    conn = httpsConn;
                } else {
                    conn = (HttpURLConnection) url.openConnection();
                }
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                for (Map.Entry<String, String> entry : hdr.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= 3) {
                    System.out.println("[QtFm] fetch failed: " + urlStr + " -> " + e.getMessage());
                    return "";
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return "";
    }

    private SSLContext getTrustAllSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{ new TrustAllManager() };
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    private String jsonStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String urlDecode(String s) {
        if (s == null) return "";
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return s;
        }
    }

    private String extractStr(String text, String key) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    private String extractNum(String text, String key) {
        if (text == null || text.isEmpty()) return "0";
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(text);
        if (m.find()) return m.group(1);
        return "0";
    }

    private String getLineName(String url) {
        if (url.contains("qtfm.cn")) return "蜻蜓FM线路";
        if (url.contains("ximalaya.com")) return "喜马拉雅线路";
        if (url.contains("touch-u.fun")) return "Touch-U";
        if (url.contains("streamtheworld.com")) return "StreamTheWorld";
        if (url.contains("ddns.net")) return "DDNS";
        if (url.contains("cdn77.org")) return "CDN77";
        if (url.contains("casthost.net")) return "CastHost";
        return "备用线路";
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"class\":[");
        for (int i = 0; i < CLASS_NAME.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type_id\":").append(jsonStr(CLASS_URL[i]))
              .append(",\"type_name\":").append(jsonStr(CLASS_NAME[i]))
              .append(",\"type_flag\":\"1\"")
              .append("}");
        }
        sb.append("],\"list\":[]}");
        return sb.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return "{\"list\":[]}";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if ("mufan".equals(tid)) {
            return getMuFanContent(pg);
        }
        if (tid != null && tid.startsWith("channel_")) {
            return getChannelCategoryContent(tid, pg);
        }

        String url = HOST + "/radiopage/" + tid + "/" + pg + "/";
        String html = fetch(url, false);
        if (html.isEmpty()) {
            return "{\"page\":" + pg + ",\"pagecount\":" + pg + ",\"limit\":" + LIMIT + ",\"total\":0,\"list\":[]}";
        }

        StringBuilder listSb = new StringBuilder();
        Pattern itemPattern = Pattern.compile("<div class=\"content-item-root c-itemS radio\">(.*?)</div>\\s*</div>", Pattern.DOTALL);
        Matcher itemMatcher = itemPattern.matcher(html);
        boolean first = true;

        while (itemMatcher.find()) {
            String item = itemMatcher.group(1);
            String title = "";
            Matcher titleMatcher = Pattern.compile("<div class=\"itemTitleRadio\" title=\"([^\"]*)\"").matcher(item);
            if (titleMatcher.find()) title = titleMatcher.group(1);
            if (title.isEmpty()) {
                Matcher spanMatcher = Pattern.compile("<span>([^<]*)</span>").matcher(item);
                if (spanMatcher.find()) title = spanMatcher.group(1).trim();
            }
            if (title.isEmpty()) title = "未知电台";

            String pic = "";
            Matcher picMatcher = Pattern.compile("<img[^>]*src=\"(//[^\"]+)\"").matcher(item);
            if (picMatcher.find()) {
                pic = picMatcher.group(1);
                if (pic.startsWith("//")) pic = "https:" + pic;
            }

            String desc = "";
            Matcher descMatcher = Pattern.compile("<div class=\"descRadio[^\"]*\"[^>]*>(.*?)</div>").matcher(item);
            if (descMatcher.find()) {
                desc = descMatcher.group(1).replaceAll("<[^>]+>", "").trim();
            }

            String vodId = "";
            Matcher hrefMatcher = Pattern.compile("<a class=\"link\" href=\"/radios/(\\d+)\"").matcher(item);
            if (hrefMatcher.find()) {
                vodId = HOST + "/radios/" + hrefMatcher.group(1);
            }

            if (!vodId.isEmpty()) {
                if (!first) listSb.append(",");
                listSb.append("{\"vod_id\":").append(jsonStr(vodId))
                      .append(",\"vod_name\":").append(jsonStr(title))
                      .append(",\"vod_pic\":").append(jsonStr(pic))
                      .append(",\"vod_remarks\":").append(jsonStr(desc))
                      .append("}");
                first = false;
            }
        }

        boolean hasNext = html.contains("paging-item-a") && html.contains("下一页");
        int page = Integer.parseInt(pg);
        int pagecount = hasNext ? page + 1 : page;
        int total = hasNext ? 9999 : 0;

        return "{\"page\":" + page + ",\"pagecount\":" + pagecount + ",\"limit\":" + LIMIT + ",\"total\":" + total + ",\"list\":[" + listSb.toString() + "]}";
    }

    private String getMuFanContent(String pg) {
        int page = Integer.parseInt(pg);
        int total = mufanStations.size();
        int pagecount = (total + LIMIT - 1) / LIMIT;
        if (pagecount < 1) pagecount = 1;

        if (page > pagecount || page < 1) {
            return "{\"page\":" + page + ",\"pagecount\":" + pagecount + ",\"limit\":" + LIMIT + ",\"total\":" + total + ",\"list\":[]}";
        }

        int start = (page - 1) * LIMIT;
        int end = Math.min(start + LIMIT, total);

        StringBuilder listSb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) listSb.append(",");
            String name = mufanStations.get(i)[0];
            String vodId = "mufan_" + i;
            listSb.append("{\"vod_id\":").append(jsonStr(vodId))
                  .append(",\"vod_name\":").append(jsonStr(name))
                  .append(",\"vod_pic\":\"\"")
                  .append(",\"vod_remarks\":\"木凡收藏\"")
                  .append("}");
        }

        return "{\"page\":" + page + ",\"pagecount\":" + pagecount + ",\"limit\":" + LIMIT + ",\"total\":" + total + ",\"list\":[" + listSb.toString() + "]}";
    }

    private String getChannelCategoryContent(String tid, String pg) {
        String channelId = tid.replace("channel_", "");
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        String[][] entries = {
            {"1", "最新"},
            {"18", "近期"},
            {"35", "较早期"},
            {"52", "历史"},
            {"69", "更早期"}
        };
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) sb.append(",");
            String pageNum = entries[i][0];
            String label = entries[i][1];
            sb.append("{\"vod_id\":").append(jsonStr(HOST + "/channels/" + channelId + "/#page=" + pageNum))
              .append(",\"vod_name\":\"车天车地车世界(").append(label).append(")\"")
              .append(",\"vod_pic\":\"https://pic.qtfm.cn/2016/0826/20160826162516947.png\"")
              .append(",\"vod_remarks\":\"广州交通广播热门汽车节目(").append(label).append(")\"")
              .append("}");
        }
        sb.append("]");
        return "{\"page\":" + pg + ",\"pagecount\":1,\"limit\":" + LIMIT + ",\"total\":5,\"list\":" + sb.toString() + "}";
    }

    public String detailContent(String id) throws Exception {
        System.out.println("[QtFm] detailContent(String) called id=" + id);
        List<String> ids = new ArrayList<>();
        ids.add(id);
        return detailContent(ids);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        System.out.println("[QtFm] detailContent called with ids=" + ids);
        StringBuilder listSb = new StringBuilder();
        boolean first = true;

        for (String rawVid : ids) {
            String vid = urlDecode(rawVid);
            System.out.println("[QtFm] decoded vid=" + vid);

            if (vid.startsWith("mufan:")) {
                String name = vid.substring(6);
                boolean found = false;
                for (int i = 0; i < mufanStations.size(); i++) {
                    if (mufanStations.get(i)[0].equals(name)) {
                        vid = "mufan_" + i;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.println("[QtFm] mufan name not found: " + name);
                    continue;
                }
            }

            if (vid.startsWith("mufan_")) {
                try {
                    int idx = Integer.parseInt(vid.substring(6));
                    if (idx >= 0 && idx < mufanStations.size()) {
                        String name = mufanStations.get(idx)[0];
                        String url = mufanStations.get(idx)[1];
                        if (url.contains("||")) {
                            String[] urls = url.split("\\|\\|");
                            StringBuilder fromSb = new StringBuilder();
                            StringBuilder urlSb = new StringBuilder();
                            for (int i = 0; i < urls.length; i++) {
                                if (i > 0) {
                                    fromSb.append("$$$");
                                    urlSb.append("$$$");
                                }
                                String u = urls[i].trim();
                                fromSb.append(getLineName(u));
                                urlSb.append(name).append("$").append(u);
                            }
                            System.out.println("[QtFm] mufan multi -> idx=" + idx + ", name=" + name);
                            if (!first) listSb.append(",");
                            listSb.append("{\"vod_id\":").append(jsonStr(vid))
                                  .append(",\"vod_name\":").append(jsonStr(name))
                                  .append(",\"vod_pic\":\"\"")
                                  .append(",\"vod_content\":\"木凡喜爱的广播\"")
                                  .append(",\"vod_play_from\":").append(jsonStr(fromSb.toString()))
                                  .append(",\"vod_play_url\":").append(jsonStr(urlSb.toString()))
                                  .append("}");
                        } else {
                            String playUrl = name + "$" + url;
                            System.out.println("[QtFm] mufan station -> idx=" + idx + ", name=" + name);
                            if (!first) listSb.append(",");
                            listSb.append("{\"vod_id\":").append(jsonStr(vid))
                                  .append(",\"vod_name\":").append(jsonStr(name))
                                  .append(",\"vod_pic\":\"\"")
                                  .append(",\"vod_content\":\"木凡喜爱的广播\"")
                                  .append(",\"vod_play_from\":\"木头的木,平凡的凡!\"")
                                  .append(",\"vod_play_url\":").append(jsonStr(playUrl))
                                  .append("}");
                        }
                        first = false;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("[QtFm] mufan idx parse error: " + e.getMessage());
                }
                continue;
            }

            if (vid.contains("/channels/") && vid.contains("/programs/")) {
                if (!first) listSb.append(",");
                listSb.append(getSingleProgramDetail(vid));
                first = false;
                continue;
            }

            if (vid.contains("/channels/")) {
                if (!first) listSb.append(",");
                listSb.append(getOndemandDetail(vid));
                first = false;
                continue;
            }

            String radioId = vid.replaceAll("/$", "");
            radioId = radioId.substring(radioId.lastIndexOf('/') + 1);
            System.out.println("[QtFm] radioId=" + radioId);

            String mUrl = "https://m.qtfm.cn/channels/" + radioId + "/";
            System.out.println("[QtFm] fetching " + mUrl);
            String html = fetch(mUrl, true);
            System.out.println("[QtFm] html length=" + html.length());

            String title = "";
            String pic = "";
            String desc = "";

            if (!html.isEmpty()) {
                Matcher scriptMatcher = Pattern.compile("window\\.__initStores\\s*=\\s*(\\{.*?\\});?</script>", Pattern.DOTALL).matcher(html);
                if (scriptMatcher.find()) {
                    String jsonBlock = scriptMatcher.group(1);
                    System.out.println("[QtFm] found __initStores, length=" + jsonBlock.length());
                    int basicIdx = jsonBlock.indexOf("\"basicInfo\"");
                    if (basicIdx >= 0) {
                        int braceStart = jsonBlock.indexOf('{', basicIdx);
                        if (braceStart >= 0) {
                            int braceCount = 0;
                            int braceEnd = braceStart;
                            for (int i = braceStart; i < jsonBlock.length(); i++) {
                                char c = jsonBlock.charAt(i);
                                if (c == '{') braceCount++;
                                else if (c == '}') {
                                    braceCount--;
                                    if (braceCount == 0) {
                                        braceEnd = i + 1;
                                        break;
                                    }
                                }
                            }
                            String basicInfo = jsonBlock.substring(braceStart, braceEnd);
                            System.out.println("[QtFm] basicInfo length=" + basicInfo.length());
                            title = extractStr(basicInfo, "name");
                            pic = extractStr(basicInfo, "cover");
                            desc = extractStr(basicInfo, "desc");
                            System.out.println("[QtFm] from basicInfo -> title=" + title + ", pic=" + pic + ", desc=" + desc);
                        }
                    }
                }

                if (title.isEmpty()) {
                    title = extractStr(html, "name");
                    System.out.println("[QtFm] fallback title=" + title);
                }
                if (pic.isEmpty()) {
                    pic = extractStr(html, "cover");
                    System.out.println("[QtFm] fallback pic=" + pic);
                }
                if (desc.isEmpty()) {
                    desc = extractStr(html, "desc");
                    System.out.println("[QtFm] fallback desc=" + desc);
                }
            }

            if (pic != null && pic.contains("!200")) pic = pic.replace("!200", "");
            if (pic != null && pic.startsWith("//")) pic = "https:" + pic;

            String playUrl = "https://lhttp.qtfm.cn/live/" + radioId + "/64k.mp3";
            String displayTitle = title.isEmpty() ? "电台-" + radioId : title;

            System.out.println("[QtFm] final -> title=" + displayTitle + ", playUrl=" + playUrl);

            if (!first) listSb.append(",");
            listSb.append("{\"vod_id\":").append(jsonStr(vid))
                  .append(",\"vod_name\":").append(jsonStr(displayTitle))
                  .append(",\"vod_pic\":").append(jsonStr(pic))
                  .append(",\"vod_content\":").append(jsonStr(desc))
                  .append(",\"vod_play_from\":\"木头的木,平凡的凡!\"")
                  .append(",\"vod_play_url\":").append(jsonStr(displayTitle + "$" + playUrl))
                  .append("}");
            first = false;
        }

        String result = "{\"list\":[" + listSb.toString() + "]}";
        System.out.println("[QtFm] detailContent result=" + result);
        return result;
    }

    private String getSingleProgramDetail(String vid) {
        Matcher match = Pattern.compile("/channels/(\\d+)/programs/(\\d+)").matcher(vid);
        if (!match.find()) {
            return getOndemandDetail(vid);
        }
        String channelId = match.group(1);
        String programId = match.group(2);
        String title = "节目-" + programId;
        String pic = "";

        try {
            String html = fetch(vid, false);
            if (!html.isEmpty()) {
                Matcher t = Pattern.compile("<title>(.*?)</title>").matcher(html);
                if (t.find()) {
                    String[] parts = t.group(1).split("-");
                    title = parts[0].trim();
                }
                Matcher p = Pattern.compile("\"cover\":\"([^\"]+)\"").matcher(html);
                if (p.find()) {
                    pic = p.group(1);
                }
            }
        } catch (Exception e) {
            System.out.println("[QtFm] singleProgram fetch error: " + e.getMessage());
        }

        if (pic.contains("!200")) pic = pic.replace("!200", "");
        if (pic.startsWith("//")) pic = "https:" + pic;

        String playUrl = "https://m.qtfm.cn/vchannels/" + channelId + "/programs/" + programId + "/";
        String playUrlStr = title + "$" + playUrl;

        return "{\"vod_id\":" + jsonStr(vid)
                + ",\"vod_name\":" + jsonStr(title)
                + ",\"vod_pic\":" + jsonStr(pic)
                + ",\"vod_content\":\"\""
                + ",\"vod_play_from\":\"木头的木,平凡的凡!\""
                + ",\"vod_play_url\":" + jsonStr(playUrlStr)
                + "}";
    }

    private String getOndemandDetail(String vid) {
        // 提取 channel_id
        Matcher cm = Pattern.compile("/channels/(\\d+)").matcher(vid);
        String channelId = cm.find() ? cm.group(1) : vid.replaceAll("/$", "").substring(vid.lastIndexOf('/') + 1).split("#")[0];
        // 解析起始页码
        int startPage = 1;
        Matcher pm = Pattern.compile("#page=(\\d+)").matcher(vid);
        if (pm.find()) startPage = Integer.parseInt(pm.group(1));
        String title = "车天车地车世界";
        String pic = "https://pic.qtfm.cn/2016/0826/20160826162516947.png";
        String desc = "广州交通广播热门汽车节目";
        // 获取频道基本信息
        String[] verInfo = fetchChannelVersion(channelId);
        String version = verInfo[0];
        if (!verInfo[2].isEmpty()) title = verInfo[2];
        if (!verInfo[3].isEmpty()) pic = verInfo[3];
        if (!verInfo[4].isEmpty()) desc = verInfo[4];
        // ---------- 多线程并发抓取17页(约510期) ----------
        int endPage = startPage + 16;
        Map<Integer, List<String[]>> results = new ConcurrentHashMap<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(endPage - startPage + 1);
        for (int page = startPage; page <= endPage; page++) {
            final int p = page;
            executor.submit(() -> {
                try {
                    String url;
                    if (p == 1) url = HOST + "/channels/" + channelId + "/";
                    else url = HOST + "/channels/" + channelId + "/" + p;
                    String html = fetch(url, false);
                    if (!html.isEmpty()) {
                        List<String[]> items = new ArrayList<>();
                        Matcher matcher = Pattern.compile("href=\"(/channels/\\d+/programs/(\\d+))\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html);
                        while (matcher.find()) {
                            String pid = matcher.group(2);
                            String ptitle = matcher.group(3).replaceAll("<[^>]+>", "").trim();
                            if (ptitle.isEmpty()) ptitle = "第" + pid + "期";
                            items.add(new String[]{ptitle, pid});
                        }
                        results.put(p, items);
                    }
                } catch (Exception e) {
                    System.out.println("[QtFm] page " + p + " fetch error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();
        // 合并结果（按页码顺序去重）
        List<String[]> allItems = new ArrayList<>();
        for (int page = startPage; page <= endPage; page++) {
            List<String[]> items = results.get(page);
            if (items != null) {
                for (String[] item : items) {
                    if (!seen.contains(item[1])) {
                        seen.add(item[1]);
                        allItems.add(item);
                    }
                }
            }
        }
        // ---------- API兜底 ----------
        if (allItems.isEmpty() && !version.isEmpty()) {
            List<Map<String, String>> progs = fetchProgramsByAPI(channelId, version);
            for (Map<String, String> p : progs) {
                String pTitle = p.get("title");
                String pId = p.get("programId");
                if (pTitle != null && !pTitle.isEmpty() && pId != null && !pId.isEmpty() && !seen.contains(pId)) {
                    seen.add(pId);
                    allItems.add(new String[]{pTitle, pId});
                }
            }
        }
        // ---------- 移动端页面兜底 ----------
        if (allItems.isEmpty()) {
            String html = fetch("https://m.qtfm.cn/vchannels/" + channelId + "/", true);
            if (html.isEmpty()) html = fetch("https://m.qtfm.cn/channels/" + channelId + "/", true);
            if (!html.isEmpty()) {
                Matcher m1 = Pattern.compile("\"programId\"\\s*:\\s*(\\d+)[^}]*\"title\"\\s*:\\s*\"([^\"]+)\"").matcher(html);
                while (m1.find()) {
                    String pid = m1.group(1);
                    String ptitle = m1.group(2);
                    if (!seen.contains(pid)) {
                        seen.add(pid);
                        allItems.add(new String[]{ptitle, pid});
                    }
                }
                if (allItems.isEmpty()) {
                    Matcher m2 = Pattern.compile("<a[^>]*href=\"(/v?channels/\\d+/programs/(\\d+))\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html);
                    while (m2.find()) {
                        String pid = m2.group(2);
                        String ptitle = m2.group(3).replaceAll("<[^>]+>", "").trim();
                        if (ptitle.isEmpty()) ptitle = "第" + pid + "期";
                        if (!seen.contains(pid)) {
                            seen.add(pid);
                            allItems.add(new String[]{ptitle, pid});
                        }
                    }
                }
            }
        }
        // ---------- 最终兜底 ----------
        if (allItems.isEmpty()) {
            allItems.add(new String[]{title, ""});
        }
        if (pic.contains("!200")) pic = pic.replace("!200", "");
        if (pic.startsWith("//")) pic = "https:" + pic;
        // ---------- 按实际年月分组为播放源 ----------
        Map<String, List<String>> ymGroups = new LinkedHashMap<>();
        for (String[] item : allItems) {
            String clean = item[0];
            String pid = item[1];
            Matcher dm = Pattern.compile("(\\d{4})(\\d{2})\\d{2}").matcher(clean);
            String ymKey;
            if (dm.find()) {
                ymKey = dm.group(1) + "年" + dm.group(2) + "月";
            } else {
                ymKey = "其他";
            }
            String url = pid.isEmpty() ? "https://m.qtfm.cn/vchannels/" + channelId + "/"
                    : "https://m.qtfm.cn/vchannels/" + channelId + "/programs/" + pid + "/";
            ymGroups.computeIfAbsent(ymKey, k -> new ArrayList<>()).add(clean + "$" + url);
        }
        StringBuilder fromSb = new StringBuilder();
        StringBuilder urlSb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : ymGroups.entrySet()) {
            if (!first) {
                fromSb.append("$$$");
                urlSb.append("$$$");
            }
            fromSb.append(entry.getKey());
            urlSb.append(String.join("#", entry.getValue()));
            first = false;
        }
        return "{\"vod_id\":" + jsonStr(vid)
                + ",\"vod_name\":" + jsonStr(title)
                + ",\"vod_pic\":" + jsonStr(pic)
                + ",\"vod_content\":" + jsonStr(desc)
                + ",\"vod_play_from\":" + jsonStr(fromSb.toString())
                + ",\"vod_play_url\":" + jsonStr(urlSb.toString())
                + "}";
    }

    private String[] fetchChannelVersion(String channelId) {
        try {
            String url = "https://webapi.qtfm.cn/api/mobile/channels/" + channelId;
            String resp = fetch(url, true);
            if (resp.isEmpty()) return new String[]{"", "0", "", "", ""};
            String v = extractStr(resp, "v");
            String programCount = extractNum(resp, "program_count");
            String title = extractStr(resp, "title");
            String cover = extractStr(resp, "cover");
            String description = extractStr(resp, "description");
            return new String[]{v, programCount, title, cover, description};
        } catch (Exception e) {
            System.out.println("[QtFm] fetchChannelVersion failed: " + e.getMessage());
            return new String[]{"", "0", "", "", ""};
        }
    }

    private List<Map<String, String>> fetchProgramsByAPI(String channelId, String version) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String url = "https://webapi.qtfm.cn/api/mobile/channels/" + channelId
                    + "/programs?version=" + version + "&pagesize=30";
            String resp = fetch(url, true);
            if (resp.isEmpty()) return result;

            Pattern p = Pattern.compile("\"programId\"\\s*:\\s*(\\d+)[^}]*?\"title\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher m = p.matcher(resp);
            while (m.find()) {
                Map<String, String> map = new HashMap<>();
                map.put("programId", m.group(1));
                map.put("title", m.group(2));
                result.add(map);
            }
        } catch (Exception e) {
            System.out.println("[QtFm] fetchProgramsByAPI failed: " + e.getMessage());
        }
        return result;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String decodedId = urlDecode(id);
        System.out.println("[QtFm] playerContent called rawId=" + id + ", decodedId=" + decodedId);

        if (decodedId.contains("/vchannels/") && decodedId.contains("/programs/")) {
            String realUrl = getVodRealUrl(decodedId);
            if (realUrl != null && !realUrl.isEmpty()) {
                Map<String, String> hdr = mHeaders;
                StringBuilder headerJson = new StringBuilder();
                headerJson.append("{");
                int count = 0;
                for (Map.Entry<String, String> entry : hdr.entrySet()) {
                    if (count > 0) headerJson.append(",");
                    headerJson.append(jsonStr(entry.getKey())).append(":").append(jsonStr(entry.getValue()));
                    count++;
                }
                headerJson.append("}");
                return "{\"parse\":0,\"playUrl\":\"\",\"url\":" + jsonStr(realUrl)
                        + ",\"header\":" + headerJson.toString() + ",\"jx\":0}";
            }
        }

        Map<String, String> hdr = headers;
        StringBuilder headerJson = new StringBuilder();
        headerJson.append("{");
        int count = 0;
        for (Map.Entry<String, String> entry : hdr.entrySet()) {
            if (count > 0) headerJson.append(",");
            headerJson.append(jsonStr(entry.getKey())).append(":").append(jsonStr(entry.getValue()));
            count++;
        }
        headerJson.append("}");

        String result = "{\"parse\":0,\"playUrl\":\"\",\"url\":" + jsonStr(decodedId)
                + ",\"header\":" + headerJson.toString() + ",\"jx\":0}";
        System.out.println("[QtFm] playerContent result=" + result);
        return result;
    }

    private String getVodRealUrl(String pageUrl) {
        try {
            String html = fetch(pageUrl, true);
            if (html.isEmpty()) return null;

            Matcher scriptMatcher = Pattern.compile("window\\.__initStores\\s*=\\s*(\\{.*?\\});?</script>", Pattern.DOTALL).matcher(html);
            String audioUrl = "";
            if (scriptMatcher.find()) {
                String jsonBlock = scriptMatcher.group(1);
                Matcher audioMatcher = Pattern.compile("\"audioUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonBlock);
                if (audioMatcher.find()) {
                    audioUrl = audioMatcher.group(1);
                }
            }

            if (audioUrl.isEmpty()) {
                Matcher m = Pattern.compile("\"audioUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(html);
                if (m.find()) {
                    audioUrl = m.group(1);
                }
            }

            if (audioUrl.isEmpty()) {
                System.out.println("[QtFm] audioUrl not found in page: " + pageUrl);
                return null;
            }

            audioUrl = audioUrl.replace("\\u0026", "&").replace("\\\\/", "/").replace("\\/", "/");
            System.out.println("[QtFm] audioUrl found: " + audioUrl);

            if (!audioUrl.startsWith("http")) {
                if (audioUrl.startsWith("//")) {
                    audioUrl = "https:" + audioUrl;
                } else {
                    return null;
                }
            }

            return followRedirect(audioUrl);

        } catch (Exception e) {
            System.out.println("[QtFm] getVodRealUrl failed " + pageUrl + ": " + e.getMessage());
            return null;
        }
    }

    private String followRedirect(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            if (urlStr.startsWith("https://")) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) url.openConnection();
                httpsConn.setSSLSocketFactory(getTrustAllSSLContext().getSocketFactory());
                httpsConn.setHostnameVerifier(new TrustAllHostnameVerifier());
                conn = httpsConn;
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            
            for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
            
            int code = conn.getResponseCode();
            System.out.println("[QtFm] followRedirect response code: " + code);
            
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                System.out.println("[QtFm] redirect location: " + location);
                if (location != null && !location.isEmpty()) {
                    if (location.startsWith("//")) {
                        location = "https:" + location;
                    } else if (location.startsWith("/")) {
                        URL baseUrl = new URL(urlStr);
                        location = baseUrl.getProtocol() + "://" + baseUrl.getHost() + location;
                    }
                    return followRedirect(location);
                }
            }
            
            if (code >= 200 && code < 300) {
                String finalUrl = conn.getURL().toString();
                System.out.println("[QtFm] final real url: " + finalUrl);
                return finalUrl;
            }
            
            return null;
        } catch (Exception e) {
            System.out.println("[QtFm] followRedirect error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick, pg, new HashMap<String, String>());
    }

    public String searchContent(String key, boolean quick, String pg, HashMap<String, String> extend) throws Exception {
        String searchKey = key;
        if (extend != null && extend.containsKey("keyword")) {
            searchKey = extend.get("keyword");
        }
        if (searchKey == null || searchKey.trim().isEmpty()) {
            return "{\"page\":" + pg + ",\"pagecount\":" + pg + ",\"limit\":" + LIMIT + ",\"total\":0,\"list\":[]}";
        }

        String encodedKey;
        try {
            encodedKey = URLEncoder.encode(searchKey, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            encodedKey = searchKey;
        }

        String searchUrl = HOST + "/search/" + encodedKey + "/";
        String html = fetch(searchUrl, false);

        StringBuilder listSb = new StringBuilder();
        boolean first = true;

        if (!html.isEmpty()) {
            Pattern itemPattern = Pattern.compile(
                "<a class=\"link\" href=\"/(radios|channels)/(\\d+)\"[^>]*>.*?<img[^>]*src=\"(//[^\"]+)\"[^>]*>.*?<div[^>]*class=\"itemTitle[^\"]*\"[^>]*>(.*?)</div>",
                Pattern.DOTALL
            );
            Matcher m = itemPattern.matcher(html);
            Set<String> seen = new HashSet<>();

            while (m.find()) {
                String type = m.group(1);
                String rid = m.group(2);
                if (seen.contains(rid)) continue;
                seen.add(rid);

                String pic = m.group(3);
                String title = m.group(4).replaceAll("<[^>]+>", "").trim();
                String picUrl = pic.startsWith("//") ? "https:" + pic : pic;
                String vodId = HOST + "/" + type + "/" + rid;

                if (!title.isEmpty()) {
                    if (!first) listSb.append(",");
                    listSb.append("{\"vod_id\":").append(jsonStr(vodId))
                          .append(",\"vod_name\":").append(jsonStr(title))
                          .append(",\"vod_pic\":").append(jsonStr(picUrl))
                          .append(",\"vod_remarks\":\"搜索\"")
                          .append("}");
                    first = false;
                }
            }
        }

        int page = Integer.parseInt(pg);
        return "{\"page\":" + page + ",\"pagecount\":" + page + ",\"limit\":" + LIMIT + ",\"total\":0,\"list\":[" + listSb.toString() + "]}";
    }
}
