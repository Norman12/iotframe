package com.praski.marcin.iotframe.Models;

import java.util.Date;

/**
 * Created by marcin on 07.09.17.
 */

public class ImageResponse {
    private String url;
    private Date date;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
