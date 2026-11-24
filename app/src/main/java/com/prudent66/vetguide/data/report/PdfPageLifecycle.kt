package com.prudent66.vetguide.data.report

/**
 * Pure-JVM page state machine mirroring Android's PdfDocument rule: a page
 * must be finished before the next one starts. Keeping this logic free of
 * Android types lets unit tests prove that multi-page rendering cannot throw
 * "Current page not finished!".
 */
internal class PdfPageLifecycle {

    var activePage: Int? = null
        private set

    var pageCount: Int = 0
        private set

    /** Starts a new page, refusing to do so while one is still active. */
    fun start(): Int {
        check(activePage == null) { "Current page not finished!" }
        pageCount += 1
        activePage = pageCount
        return pageCount
    }

    /** Finishes the active page and returns its number. */
    fun finish(): Int {
        val page = checkNotNull(activePage) { "No active page to finish" }
        activePage = null
        return page
    }

    /** Finishes any active page at the end of rendering. */
    fun finishAll() {
        if (activePage != null) finish()
    }
}
