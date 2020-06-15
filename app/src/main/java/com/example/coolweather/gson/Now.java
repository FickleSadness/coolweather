package com.example.coolweather.gson;

import com.google.gson.annotations.SerializedName;

public class Now {

    /**
     * "now":{ "tmp":"29", "cond":{ "txt":"阵雨" } }
     */

    @SerializedName("tmp")
    public String temperature;

    @SerializedName("cond")
    public More more;

    public class More {
        @SerializedName("txt")
        public String info;
    }
}
