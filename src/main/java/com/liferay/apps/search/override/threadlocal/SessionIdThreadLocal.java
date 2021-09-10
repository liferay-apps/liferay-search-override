package com.liferay.apps.search.override.threadlocal;

import com.liferay.petra.lang.CentralizedThreadLocal;

public class SessionIdThreadLocal {

    public static String getSessionId() {
        return _sessionId.get();
    }

    public static void setSessionId(String sessionId) {
        _sessionId.set(sessionId);
    }

    private static final ThreadLocal<String> _sessionId = new CentralizedThreadLocal<>(
            SessionIdThreadLocal.class + "._sessionId"
    );

}