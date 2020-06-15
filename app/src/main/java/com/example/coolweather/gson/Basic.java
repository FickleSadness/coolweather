package com.example.coolweather.gson;

import com.google.gson.annotations.SerializedName;

public class Basic {

    /**
     * "basic":{ "city":"苏州", "id":"CN101190401", "update":{ "loc":"2016-08-08 21:58" } }
     * 其中，city表示城市名，id表示城市对应的天气id，update中的loc表示天气的更新时间。
     */

    @SerializedName("city")
    public String cityName;

    @SerializedName("id")
    public String weatherId;

    public Update update;

    public class Update {
        @SerializedName("loc")
        public String updateTime;
    }
}
