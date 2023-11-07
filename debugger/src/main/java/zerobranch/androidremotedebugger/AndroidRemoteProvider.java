package zerobranch.androidremotedebugger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import top.canyie.pine.PineConfig;
import zerobranch.androidremotedebugger.logging.NetLoggingInterceptor;

/**
 * Created by cy on 2023/11/7.
 */
public class AndroidRemoteProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return true;
        boolean isApkInDebug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        PineConfig.debug = isApkInDebug; // 是否debug，true会输出较详细log
        PineConfig.debuggable = isApkInDebug; // 该应用是否可调试，建议和配置文件中的值保持一致，否则会出现问题
        NetLoggingInterceptor netLoggingInterceptor = new NetLoggingInterceptor();

        XposedHelpers.findAndHookConstructor(
                OkHttpClient.class,
                OkHttpClient.Builder.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        OkHttpClient.Builder builder = (OkHttpClient.Builder) param.args[0];
                        Iterator<Interceptor> iterator = builder.interceptors().iterator();
                        while (iterator.hasNext()) {
                            Interceptor interceptor = iterator.next();
                            if (interceptor instanceof NetLoggingInterceptor) {
                                iterator.remove();
                            }
                        }
                        builder.addInterceptor(netLoggingInterceptor);
                    }
                }
        );
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
