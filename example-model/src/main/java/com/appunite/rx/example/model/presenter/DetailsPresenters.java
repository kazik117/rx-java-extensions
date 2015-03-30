package com.appunite.rx.example.model.presenter;

import com.appunite.rx.ObservableExtensions;
import com.appunite.rx.ResponseOrError;
import com.appunite.rx.dagger.NetworkScheduler;
import com.appunite.rx.dagger.UiScheduler;
import com.appunite.rx.example.model.dao.ItemsDao;
import com.appunite.rx.example.model.model.ItemWithBody;
import com.appunite.rx.functions.Functions1;
import com.appunite.rx.functions.FunctionsN;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

import static com.google.common.base.Preconditions.checkNotNull;

public class DetailsPresenters {

    @Nonnull
    private final Scheduler networkScheduler;
    @Nonnull
    private final Scheduler uiScheduler;
    @Nonnull
    private final ItemsDao itemsDao;

    public DetailsPresenters(@Nonnull @NetworkScheduler Scheduler networkScheduler,
                             @Nonnull @UiScheduler Scheduler uiScheduler,
                             @Nonnull ItemsDao itemsDao) {

        this.networkScheduler = networkScheduler;
        this.uiScheduler = uiScheduler;
        this.itemsDao = itemsDao;
    }

    @Nonnull
    public DetailsPresenter getPresenter(@Nonnull final String id) {
        checkNotNull(id);
        return new DetailsPresenter(id);
    }

    public class DetailsPresenter {

        private final ItemsDao.ItemDao itemDao;
        private final Observable<ResponseOrError<String>> nameObservable;
        private final Observable<ResponseOrError<String>> bodyObservable;

        public DetailsPresenter(@Nonnull String id) {
            itemDao = itemsDao.itemDao(id);

            nameObservable = itemDao.dataObservable()
                    .compose(ResponseOrError.map(new Func1<ItemWithBody, String>() {
                        @Override
                        public String call(ItemWithBody item) {
                            return item.name();
                        }
                    }))
                    .subscribeOn(networkScheduler)
                    .observeOn(uiScheduler)
                    .compose(ObservableExtensions.<ResponseOrError<String>>behaviorRefCount());

            bodyObservable = itemDao.dataObservable()
                    .compose(ResponseOrError.map(new Func1<ItemWithBody, String>() {
                        @Override
                        public String call(ItemWithBody item) {
                            return item.body();
                        }
                    }))
                    .subscribeOn(networkScheduler)
                    .observeOn(uiScheduler)
                    .compose(ObservableExtensions.<ResponseOrError<String>>behaviorRefCount());
        }

        public Observable<String> bodyObservable() {
            return bodyObservable
                    .compose(ResponseOrError.<String>onlySuccess());
        }

        public Observable<String> titleObservable() {
            return nameObservable
                    .compose(ResponseOrError.<String>onlySuccess());
        }

        public Observable<Boolean> progressObservable() {
            return Observable.combineLatest(
                    Arrays.asList(nameObservable, bodyObservable),
                    FunctionsN.returnFalse())
                    .startWith(true);
        }

        public Observable<Throwable> errorObservable() {
            return Observable.combineLatest(Arrays.asList(
                            nameObservable.map(ResponseOrError.toNullableThrowable()).startWith((Throwable) null),
                            bodyObservable.map(ResponseOrError.toNullableThrowable()).startWith((Throwable) null)
                    ),
                    FunctionsN.combineFirstThrowable())
                    .startWith((Throwable) null)
                    .distinctUntilChanged();
        }


        public Observable<Object> startPostponedEnterTransitionObservable() {
            final Observable<Boolean> filter = progressObservable().filter(Functions1.isFalse());
            final Observable<Throwable> error = errorObservable().filter(Functions1.isNotNull());
            final Observable<String> timeout = Observable.just("").delay(500, TimeUnit.MILLISECONDS, uiScheduler);
            return Observable.<Object>amb(filter, error, timeout);
        }
    }

}
