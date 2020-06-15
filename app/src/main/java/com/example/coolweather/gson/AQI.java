package com.example.coolweather.gson;

public class AQI {

    /**
     * "aqi":{ "city":{ "aqi":"44", "pm25":"13" } }
     */

    public AQICity city;

    public class AQICity {
        public String aqi;
        public String pm25;
    }
}
