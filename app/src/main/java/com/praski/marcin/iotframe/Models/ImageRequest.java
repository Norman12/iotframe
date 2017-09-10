package com.praski.marcin.iotframe.Models;

/**
 * Created by marcin on 09.09.17.
 */

public class ImageRequest {
    private String mime;
    private String data;

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
