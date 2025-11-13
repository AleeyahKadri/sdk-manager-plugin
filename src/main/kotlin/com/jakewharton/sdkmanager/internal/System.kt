package com.jakewharton.sdkmanager.internal

/** An indirection to [java.lang.System]. */
interface System {
    fun env(name: String): String?
    fun property(key: String): String?
    fun property(key: String, defaultValue: String): String?

    class Real : System {
        override fun env(name: String): String? {
            return java.lang.System.getenv(name)
        }

        override fun property(key: String): String? {
            return java.lang.System.getProperty(key)
        }

        override fun property(key: String, defaultValue: String): String? {
            return java.lang.System.getProperty(key, defaultValue)
        }
    }
}
