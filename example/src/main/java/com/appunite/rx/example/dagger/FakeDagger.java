package com.appunite.rx.example.dagger;

import android.content.Context;
import android.support.annotation.NonNull;

import com.appunite.gson.AndroidUnderscoreNamingStrategy;
import com.appunite.gson.ImmutableListDeserializer;
import com.appunite.rx.android.MyAndroidNetworkSchedulers;
import com.appunite.rx.example.auth.FirebaseCurrentLoggedInUserDao;
import com.appunite.rx.example.model.api.GuestbookService;
import com.appunite.rx.example.model.dao.MyCurrentLoggedInUserDao;
import com.appunite.rx.example.model.dao.PostsDao;
import com.appunite.rx.example.model.helpers.CacheProvider;
import com.appunite.rx.example.model.helpers.DiskCacheCreator;
import com.appunite.rx.subjects.CacheSubject;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.lang.reflect.Type;

import javax.annotation.Nonnull;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Normally we rather use dagger instead of static, but for testing purposes is ok
 */
public class FakeDagger {

    private static final Object LOCK = new Object();
    private static PostsDao postsDao;
    private static MyCurrentLoggedInUserDao currentLoggedInUserDao;

    public static MyCurrentLoggedInUserDao getCurrentLoggedInUserDaoInstance() {
        synchronized (LOCK) {
            if (currentLoggedInUserDao != null) {
                return currentLoggedInUserDao;
            }
            currentLoggedInUserDao = new FirebaseCurrentLoggedInUserDao();
            return currentLoggedInUserDao;
        }
    }

    public static PostsDao getPostsDaoInstance(@Nonnull final Context context) {
        synchronized (LOCK) {
            if (postsDao != null) {
                return postsDao;
            }
            final Gson gson = getGson();
            final OkHttpClient client = getOkHttpClient(context);
            final Retrofit restAdapter = getRestAdapter(gson, client);
            final GuestbookService guestbookService = restAdapter.create(GuestbookService.class);
            final CacheProvider cacheProvider = getCacheProvider(context, gson);
            postsDao = new PostsDao(MyAndroidNetworkSchedulers.networkScheduler(), guestbookService, cacheProvider, getCurrentLoggedInUserDaoInstance());
            return postsDao;
        }
    }

    @NonNull
    private static OkHttpClient getOkHttpClient(@Nonnull Context context) {
        final File cacheDirectory = new File(context.getCacheDir(), "ok-http");
        return new OkHttpClient.Builder()
                .cache(getCache(cacheDirectory))
                .build();
    }

    @Nonnull
    private static Retrofit getRestAdapter(@Nonnull Gson gson, @Nonnull OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl("https://atlantean-field-90117.appspot.com/_ah/api/guestbook/")
                .client(client)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    @NonNull
    private static CacheProvider getCacheProvider(@Nonnull final Context context, final Gson gson) {
        return new CacheProvider() {
                    @Nonnull
                    @Override
                    public <T> CacheSubject.CacheCreator<T> getCacheCreatorForKey(@Nonnull String key, @Nonnull Type type) {
                        return new DiskCacheCreator<>(gson, type, new File(context.getCacheDir(), key + ".txt"));
                    }
                };
    }

    @NonNull
    private static Gson getGson() {
        return new GsonBuilder()
                        .registerTypeAdapter(ImmutableList.class, new ImmutableListDeserializer())
                        .setFieldNamingStrategy(new AndroidUnderscoreNamingStrategy())
                        .create();
    }

    @Nonnull
    private static Cache getCache(@Nonnull File cacheDirectory) {
        long cacheSize = 10L * 1024L * 1024L; // 10 MiB
        return new Cache(cacheDirectory, cacheSize);
    }
}
