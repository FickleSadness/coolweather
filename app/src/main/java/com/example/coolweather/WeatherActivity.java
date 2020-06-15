package com.example.coolweather;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.coolweather.gson.Forecast;
import com.example.coolweather.gson.Weather;
import com.example.coolweather.service.AutoUpdateService;
import com.example.coolweather.util.HttpUtil;
import com.example.coolweather.util.Utility;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class WeatherActivity extends AppCompatActivity {

    @BindView(R.id.weather_layout)
    ScrollView weatherLayout;//滚动条
    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefresh;//刷新条
    @BindView(R.id.drawer_layout)
    DrawerLayout drawerLayout;//滑动菜单功能
    @BindView(R.id.title_city)
    TextView titleCity;//城市名
    @BindView(R.id.title_update_time)
    TextView titleUpdateTime;//更新时间
    @BindView(R.id.degree_text)
    TextView degreeText;//当前气温
    @BindView(R.id.weather_info_text)
    TextView weatherInfoText;//天气概况
    @BindView(R.id.forecast_layout)
    LinearLayout forecastLayout;//未来几天天气信息的布局
    @BindView(R.id.aqi_text)
    TextView aqiText;//AQI指数
    @BindView(R.id.pm25_text)
    TextView pm52Text;//PM2.5指数
    @BindView(R.id.comfort_text)
    TextView comfortText;//舒适度
    @BindView(R.id.car_wash_text)
    TextView carWashText;//洗车指数
    @BindView(R.id.sport_text)
    TextView sportText;//运动建议
    @BindView(R.id.bing_pic_img)
    ImageView bingPicImg;//背景图片
    @BindView(R.id.nav_button)
    Button navButton;//切换城市的按钮
    private String mWeatherId;//用于记录城市的天气id


    /**
     * 在onCreate() 方法中仍然先是去获取一些控件的实例，然后会尝试从本地缓存中读取天气数据。
     * 那么第一次肯定是没有缓存的，因此就会从Intent中取出天气id，并调用requestWeather()方法来从服务器请求天气数据。
     * 注意，请求数据的时候先将ScrollView进行隐藏，不然空数据的界面看上去会很奇怪。
     *
     * @param savedInstanceState savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //实现让背景图和状态栏融合到一起的效果
        if (Build.VERSION.SDK_INT >= 21) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);//表示活动的布局会显示在状态栏上面
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
        setContentView(R.layout.activity_weather);

        ButterKnife.bind(this);// 初始化各控件

        //本地缓存
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //读取天气数据
        String weatherString = prefs.getString("weather", null);
        if (weatherString != null) {
            // 有缓存时直接解析天气数据
            Weather weather = Utility.handleWeatherResponse(weatherString);
            mWeatherId = weather.basic.weatherId;
            showWeatherInfo(weather);
        } else {
            // 无缓存时去服务器查询天气
            mWeatherId = getIntent().getStringExtra("weather_id");
            weatherLayout.setVisibility(View.INVISIBLE);
            requestWeather(mWeatherId);
        }

        //下拉刷新
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);//设置下拉刷新进度条的颜色
        //调用setOnRefreshListener()方法来设置一个下拉刷新的监听器，当触发了下拉刷新操作的时候，
        //就会回调这个监听器的onRefresh()方法，我们在这里去调用requestWeather()方法请求天气信息就可以了。
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                requestWeather(mWeatherId);
            }
        });

        //调用DrawerLayout的openDrawer()方法来打开滑动菜单
        navButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        //设置背景图片
        String bing_pic = prefs.getString("bing_pic", null);
        if (bing_pic != null) {
            //如果有缓存的话就直接使用Glide来加载这张图片
            Glide.with(this).load(bing_pic).into(bingPicImg);
        } else {
            //如果没有的话就调用loadBingPic()方法去请求今日的必应背景图。
            loadBingPic();
        }
    }

    /**
     * 根据天气id请求城市天气信息
     * requestWeather()方法中先是使用了参数中传入的天气id和我们之前申请好的APIKey拼装出一个接口地址，
     * 接着调用HttpUtil.sendOkHttpRequest()方法来向该地址发出请求，服务器会将相应城市的天气信息以JSON格式返回。
     * 然后我们在onResponse()回调中先调用Utility.handleWeatherResponse()方法将返回的JSON数据转换成Weather对象，
     * 再将当前线程切换到主线程。然后进行判断，如果服务器返回的status状态是ok，就说明请求天气成功了，
     * 此时将返回的数据缓存到SharedPreferences当中，并调用showWeatherInfo()方法来进行内容显示。
     * 另外需要注意，在requestWeather()方法的最后也需要调用一下loadBingPic()方法，这样在每次请求天气信息的时候同时也会刷新背景图片。
     *
     * @param weatherId 天气id
     */
    public void requestWeather(String weatherId) {
        String weatherUrl = "http://guolin.tech/api/weather?cityid=" + weatherId +
                "&key=bc0418b57b2d4918819d3974ac1285d9";
        HttpUtil.sendOkHttpRequest(weatherUrl, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WeatherActivity.this, "获取天气信息失败",
                                Toast.LENGTH_SHORT).show();
                        //另外不要忘记，当请求结束后，还需要调用SwipeRefreshLayout的setRefreshing()方法并传入false，
                        //用于表示刷新事件结束，并隐藏刷新进度条。
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseText = response.body().string();
                Weather weather = Utility.handleWeatherResponse(responseText);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (weather != null && "ok".equals(weather.status)) {
                            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences
                                    (WeatherActivity.this).edit();
                            editor.putString("weather", responseText);
                            editor.apply();
                            mWeatherId = weather.basic.weatherId;
                            showWeatherInfo(weather);
                        } else {
                            Toast.makeText(WeatherActivity.this, "获取天气信息失败",
                                    Toast.LENGTH_SHORT).show();
                        }
                        //另外不要忘记，当请求结束后，还需要调用SwipeRefreshLayout的setRefreshing()方法并传入false，
                        //用于表示刷新事件结束，并隐藏刷新进度条。
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        });
        loadBingPic();
    }

    /**
     * 处理并展示Weather实体类中的数据
     * showWeatherInfo()方法中的逻辑就比较简单了，其实就是从Weather对象中获取数据，然后显示到相应的控件上。
     * 注意在未来几天天气预报的部分我们使用了一个for循环来处理每天的天气信息，
     * 在循环中动态加载forecast_item.xml布局并设置相应的数据，然后添加到父布局当中。
     * 设置完了所有数据之后，记得要将ScrollView重新变成可见。
     *
     * @param weather weather
     */
    private void showWeatherInfo(Weather weather) {
        String cityName = weather.basic.cityName;//获取城市名
        String updateTime = weather.basic.update.updateTime.split(" ")[1];//获取天气的更新时间
        String degree = weather.now.temperature + "°C";//获取当前气温
        String weatherInfo = weather.now.more.info;//获取天气概况
        titleCity.setText(cityName);//设置城市名
        titleUpdateTime.setText(updateTime);//设置天气的更新时间
        degreeText.setText(degree);//设置当前气温
        weatherInfoText.setText(weatherInfo);//设置天气概况
        forecastLayout.removeAllViews();//移除所有视图
        for (Forecast forecast : weather.forecastList) {
            View view = LayoutInflater.from(this).inflate(R.layout.forecast_item, forecastLayout,
                    false);//动态加载forecast_item.xml布局
            TextView dataText = view.findViewById(R.id.date_text);//天气预报日期
            TextView infoText = view.findViewById(R.id.info_text);//天气概况
            TextView maxText = view.findViewById(R.id.max_text);//最高温度
            TextView minText = view.findViewById(R.id.min_text);//最低温度
            dataText.setText(forecast.date);//设置天气预报日期
            infoText.setText(forecast.more.info);//设置天气概况
            maxText.setText(forecast.temperature.max);//设置最高温度
            minText.setText(forecast.temperature.min);//设置最低温度
            forecastLayout.addView(view);//添加
        }
        if (weather.aqi != null) {
            aqiText.setText(weather.aqi.city.aqi);//设置AQI指数
            pm52Text.setText(weather.aqi.city.pm25);//设置PM2.5指数
        }
        String comfort = "舒适度：" + weather.suggestion.comfort.info;//获取舒适度
        String carWash = "洗车指数：" + weather.suggestion.carWash.info;//获取洗车指数
        String sport = "运动建议：" + weather.suggestion.sport.info;//获取运动建议
        comfortText.setText(comfort);//设置舒适度
        carWashText.setText(carWash);//设置洗车指数
        sportText.setText(sport);//设置运动建议
        weatherLayout.setVisibility(View.VISIBLE);//将ScrollView重新变成可见
        //激活AutoUpdateService这个服务
        Intent intent = new Intent(this, AutoUpdateService.class);
        startService(intent);
    }

    /**
     * 加载必应每日一图
     * 先是调用了HttpUtil.sendOkHttpRequest()方法获取到必应背景图的链接，然后将这个链接缓存到 SharedPreferences当中，
     * 再将当前线程切换到主线程，最后使用Glide来加载这张图片就可以了。
     */
    private void loadBingPic() {
        String requestBingPic = "http://guolin.tech/api/bing_pic";
        HttpUtil.sendOkHttpRequest(requestBingPic, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String bingPic = response.body().string();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences
                        (WeatherActivity.this).edit();
                editor.putString("bing_pic", bingPic);
                editor.apply();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Glide.with(WeatherActivity.this).load(bingPic).into(bingPicImg);
                    }
                });
            }
        });
    }
}