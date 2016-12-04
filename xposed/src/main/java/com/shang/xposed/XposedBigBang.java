package com.shang.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.shang.xposed.TouchEventHandler.BIG_BANG_RESPONSE_TIME;
import static com.shang.xposed.XposedConstant.PACKAGE_NAME;
import static com.shang.xposed.XposedConstant.SP_DISABLE_KEY;
import static com.shang.xposed.XposedConstant.SP_DOBLUE_CLICK;
import static com.shang.xposed.XposedConstant.SP_NAME;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by dim on 16/10/23.
 */

public class XposedBigBang implements IXposedHookLoadPackage {

    private static final String TAG = "XposedBigBang";


    private final TouchEventHandler mTouchHandler = new TouchEventHandler();
    private final TouchEventHandler mTouchTextViewHandler = new TouchEventHandler();
    private final List<Filter> mFilters = new ArrayList<>();
    private XSharedPreferences appXSP;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        Log.e("shang", "xposed-packageName:" + loadPackageParam.packageName);
        setXpoedEnable(loadPackageParam);
//        if (!new File("/data/data/"+PACKAGE_NAME).exists())
//            return;
        //  wakeup(loadPackageParam);
        // Logger.d(TAG, loadPackageParam.packageName);
        appXSP = new XSharedPreferences(PACKAGE_NAME, SP_NAME);
        appXSP.makeWorldReadable();
        Set<String> disAppSet = appXSP.getStringSet(SP_DISABLE_KEY, null);
        if (disAppSet != null && disAppSet.contains(loadPackageParam.packageName)) {
            return;
        }

//        XSharedPreferences appSp = new XSharedPreferences(PACKAGE_NAME, MAINSPNAME);
//        appSp.makeWorldReadable();
        //只用点击
        mFilters.add(new Filter.TextViewValidFilter());
        //优化微信 下的体验。
        if ("com.tencent.mm".equals(loadPackageParam.packageName)) {
            //朋友圈内容拦截。
            mFilters.add(new Filter.WeChatValidFilter(loadPackageParam.classLoader));
            //聊天详情中的文字点击事件优化
            try {
                findAndHookMethod(loadPackageParam.classLoader.loadClass("com.tencent.mm.ui.base.MMTextView"), "onTouchEvent",
                        MotionEvent.class, new MMTextViewTouchEvent());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            try {
                findAndHookMethod(loadPackageParam.classLoader.loadClass("com.tencent.mm.ui.widget.MMTextView"), "onTouchEvent",
                        MotionEvent.class, new MMTextViewTouchEvent());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        // installer  不注入。 防止代码出错。进不去installer 中。
        if (!"de.robv.android.xposed.installer".equals(loadPackageParam.packageName)) {
            //findAndHookMethod(Activity.class, "onTouchEvent", MotionEvent.class, new ActivityTouchEvent());
            findAndHookMethod(View.class, "onTouchEvent", MotionEvent.class, new TextViewTouchEvent(loadPackageParam.packageName));
        }
    }
//        } else {
//            //使用force touch
//            mFilters.add(new Filter.TextViewValidFilter());
//            //优化微信 下的体验。
//            mFilters.add(new Filter.WeChatValidFilter(loadPackageParam.classLoader));
//
//            // installer  不注入。 防止代码出错。进不去installer 中。
//            if (!"de.robv.android.xposed.installer".equals(loadPackageParam.packageName)) {
//                // findAndHookMethod(Activity.class, "onTouchEvent", MotionEvent.class, new ActivityTouchEvent());
//                findAndHookMethod(TextView.class, "onTouchEvent", MotionEvent.class, new ForceTouchTextViewTouchEvent(loadPackageParam.packageName));
//            }
//        }


//    private void wakeup(XC_LoadPackage.LoadPackageParam loadPackageParam) {
//        findAndHookMethod("android.app.Application", loadPackageParam.classLoader, "onCreate", new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                Context context = (Context) param.thisObject;
//
//                Intent intent = new Intent();
//                intent.setAction("com.shang.bigbang.wake");
//                intent.addCategory(Intent.CATEGORY_DEFAULT);
//                context.sendBroadcast(intent);
//            }
//
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                super.afterHookedMethod(param);
//            }
//        });
//    }


    private void setXpoedEnable(XC_LoadPackage.LoadPackageParam loadPackageParam) throws ClassNotFoundException {
        if (loadPackageParam.packageName.equals(PACKAGE_NAME)) {
            findAndHookMethod(loadPackageParam.classLoader.loadClass("com.shang.xposed.XposedEnable"), "isEnable", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    return true;
                }
            });
        }
    }

    private Set<String> getLauncherAsWhiteList(Context c) {
        HashSet<String> packages = new HashSet<>();
        PackageManager packageManager = c.getPackageManager();
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
//        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : resolveInfo) {
            packages.add(ri.activityInfo.packageName);
        }
        return packages;
    }

    private Set<String> getInputMethodAsWhiteList(Context context) {
        HashSet<String> packages = new HashSet<>();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> methodList = imm.getInputMethodList();
        for (InputMethodInfo info : methodList) {
            packages.add(info.getPackageName());
        }
        return packages;
    }

    private class MMTextViewTouchEvent extends XC_MethodHook {

        private boolean intercept = false;

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);
            //拦截聊天界面快速点击进入信息详情
            View view = (View) param.thisObject;
            MotionEvent event = (MotionEvent) param.args[0];

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    long preClickTimeMillis = mTouchHandler.getClickTimeMillis(view);
                    long currentTimeMillis = System.currentTimeMillis();
                    if (preClickTimeMillis != 0) {
                        long interval = currentTimeMillis - preClickTimeMillis;
                        if (interval < BIG_BANG_RESPONSE_TIME) {
                            intercept = true;
                        } else {
                            intercept = false;
                        }
                    } else {
                        intercept = false;
                    }
                    break;
            }
            mTouchHandler.hookTouchEvent(view, event, mFilters, true, appXSP.getInt(SP_DOBLUE_CLICK,1000));
            if (intercept) {
                param.setResult(true);
            }

        }
    }

    private class ForceTouchTextViewTouchEvent extends XC_MethodHook {


        private final String packageName;

        public ForceTouchTextViewTouchEvent(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);
            //拦截聊天界面快速点击进入信息详情
            View view = (View) param.thisObject;
            MotionEvent event = (MotionEvent) param.args[0];
            if (isKeyBoardOrLauncher(view.getContext(), packageName))
                return;

            //   boolean intercept = mTouchHandler.hookForceTouchEvent(view, event, mFilters, true);
//            if (intercept) {
//                param.setResult(true);
//            }

        }
    }

    private class TextViewTouchEvent extends XC_MethodHook {


        private final String packageName;
        private boolean intercept;

        public TextViewTouchEvent(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);
            //拦截聊天界面快速点击进入信息详情
            View view = (View) param.thisObject;
            MotionEvent event = (MotionEvent) param.args[0];
            if (isKeyBoardOrLauncher(view.getContext(), packageName))
                return;

//            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN:
//                    long preClickTimeMillis = mTouchTextViewHandler.getViewClickTimeMillis(view);
//                    long currentTimeMillis = System.currentTimeMillis();
//                        long interval = currentTimeMillis - preClickTimeMillis;
//                        if (interval < TouchEventHandler.BIG_BANG_RESPONSE_TIME) {
//                            intercept = true;
//                        } else {
//                            intercept = false;
//                        }
//                    mTouchTextViewHandler.setViewClickTimeMillis(view,System.currentTimeMillis());
////                    } else {
////                        intercept = false;
////                        mTouchTextViewHandler.setViewClickTimeMillis(view,System.currentTimeMillis());
////                    }
//                    break;
//            }
            mTouchTextViewHandler.hookAllTouchEvent(view, event, mFilters, true,appXSP.getInt(SP_DOBLUE_CLICK,1000));
            if (intercept) {
                param.setResult(true);
            }

        }
    }

    private boolean isKeyBoardOrLauncher(Context context, String packageName) {
        if (context == null)
            return true;
        for (String package_process : getInputMethodAsWhiteList(context)) {
            if (package_process.equals(packageName))
                return true;
        }
        for (String package_process : getLauncherAsWhiteList(context)) {
            if (package_process.equals(packageName))
                return true;
        }

        return false;
    }


    private class ActivityTouchEvent extends XC_MethodHook {

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);
            Activity activity = (Activity) param.thisObject;
            View view = activity.findViewById(android.R.id.content);
            MotionEvent event = (MotionEvent) param.args[0];
          //  Log.e("shang", "activityTouchEvent: " + event);
            //mTouchHandler.hookTouchEvent(view, event, mFilters, false);
        }
    }


    private class ViewTouchEvent extends XC_MethodHook {

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);

            if ((Boolean) param.getResult()) {
                View view = (View) param.thisObject;
                MotionEvent event = (MotionEvent) param.args[0];
                Logger.d(TAG, view.getClass().getSimpleName());
                Logger.d(TAG, "viewTouchEvent: " + event.getAction());
                mTouchHandler.hookTouchEvent(view, event, mFilters, false, appXSP.getInt(SP_DOBLUE_CLICK, 1000));
            }
        }
    }

}