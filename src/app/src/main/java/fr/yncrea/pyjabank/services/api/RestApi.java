package fr.yncrea.pyjabank.services.api;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import fr.yncrea.pyjabank.database.models.Account;
import fr.yncrea.pyjabank.database.models.User;
import fr.yncrea.pyjabank.database.BankDatabase;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

public class RestApi<T> {

    private interface ApiRoutes { //Create Read Update Delete
        @GET("m1/config/1")
        Call<User> readUser();//(@Field("username") String username, @Field("password") String password)

        @GET("m1/accounts")
        Call<ArrayList<Account>> readAccounts();//(@Field("username") String username)
    }

    private static final ApiRoutes apiInterface = new Retrofit.Builder()
            .baseUrl("https://6007f1a4309f8b0017ee5022.mockapi.io/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiRoutes.class);

    private final Activity mActivity;
    private static final Executor mBackgroundThread = Executors.newSingleThreadExecutor();

    private int mFlag = -1;
    private static final int FLAG_READ_USER = 0;
    private static final int FLAG_READ_ACCOUNT = 1;

    private Handler mHandler;
    private static final int EXECUTION_RESULT = 0;
    private static final int RESPONSE_SUCCESS = 0;
    private static final int RESPONSE_EMPTY = 1;
    private static final int RESPONSE_ERROR = 2;

    public RestApi(Activity activity) {
        mActivity = activity;
    }

    public RestApi<T> setHandler(Runnable successFunc, Runnable emptyFunc, Runnable errorFunc) {
        mActivity.runOnUiThread(() ->
            mHandler = new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == EXECUTION_RESULT) {
                        switch (msg.arg1) {
                            case RESPONSE_SUCCESS:
                                successFunc.run();
                                break;
                            case RESPONSE_EMPTY:
                                emptyFunc.run();
                                break;
                            case RESPONSE_ERROR:
                                errorFunc.run();
                                break;
                        }
                    }
                }
            }
        );

        return this;
    }

    @SuppressWarnings("unchecked")
    public void retrieveStoreAccountList(BankDatabase db) { //(db, username)
        mFlag = FLAG_READ_ACCOUNT;
        retrieveStoreData((Call<T>) apiInterface.readAccounts(), db);
    }

    @SuppressWarnings("unchecked")
    public void retrieveStoreUser(BankDatabase db) { //(db, username, password)
        mFlag = FLAG_READ_USER;
        retrieveStoreData((Call<T>) apiInterface.readUser(), db);
    }

    private void retrieveStoreData(Call<T> request, BankDatabase db) {//(db, username, optPassword)
        mBackgroundThread.execute(() -> {
            try {
                Response<T> response = request.execute();

                if (response.isSuccessful() && response.body() != null) {
                    T data = response.body();

                    switch (mFlag) {
                        case FLAG_READ_USER:
                            if (data instanceof User) {
                                db.userDao().insert((User) data);
                            }
                            else throw new Exception("Unexpected type of data");
                            break;

                        case FLAG_READ_ACCOUNT:
                            if (data instanceof ArrayList) {
                                @SuppressWarnings("unchecked")
                                ArrayList<Account> typedData = (ArrayList<Account>) data;
                                db.accountDao().insertAll(typedData);
                            }
                            else throw new Exception("Unexpected type of data");
                            break;

                        default:
                            throw new Exception("Unexpected flag");
                    }

                    if (mHandler != null) mHandler.obtainMessage(EXECUTION_RESULT, RESPONSE_SUCCESS, -1).sendToTarget();
                }
                else {
                    if (mHandler != null) mHandler.obtainMessage(EXECUTION_RESULT, RESPONSE_EMPTY, -1).sendToTarget();
                }
            }
            catch (Exception e) {
                if (mHandler != null) mHandler.obtainMessage(EXECUTION_RESULT, RESPONSE_ERROR, -1).sendToTarget();
            }
        });
    }
}