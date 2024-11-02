
package fi.iki.murgo.irssinotifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.RequestBody;

public class MessageToServer {
    private static final String LANGUAGE = "language";
    private static final String VERSION = "version";

    private static int version;

    private Map<String, String> map = new HashMap<String, String>();

    public MessageToServer() {
        this(null);
    }

    public MessageToServer(Map<String, String> values) {
        map.put(LANGUAGE, Locale.getDefault().getISO3Language());
        map.put(VERSION, Integer.toString(version));
        if (values != null) {
            for (Map.Entry<String, String> pair : values.entrySet()) {
                map.put(pair.getKey(), pair.getValue());
            }
        }
    }

    public Map<String, String> getMap() {
        return this.map;
    }

    public static void setVersion(int versionCode) {
        version = versionCode;
    }

    public String getHttpString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        return sb.toString();
    }

    public RequestBody getRequestBody() {
        FormBody.Builder formBody = new FormBody.Builder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            formBody.add(entry.getKey(), entry.getValue());
        }
        return formBody.build();
    }


    public String getUrlWithParameters(String url) {
        HttpUrl.Builder urlBuilder
                = HttpUrl.parse(url).newBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
        }

        return urlBuilder.build().toString();
    }
}
