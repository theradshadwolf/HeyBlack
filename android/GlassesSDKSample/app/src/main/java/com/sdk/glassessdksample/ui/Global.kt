package com.sdk.glassessdksample.ui
import android.view.View

/**
 * @Author: Hzy
 * @CreateDate: 2021/6/25 14:14
 * <p>
 * "Programs must be written for people to read,
 * and only incidentally for machines to execute"
 *
 */
/**
 * Set click listeners on multiple views at once.
 *
 * @param v the views to attach the listener to
 * @param block callback block invoked on each click event
 */
fun setOnClickListener(vararg v: View?, block: View.() -> Unit) {
    val listener = View.OnClickListener { it.block() }
    v.forEach { it?.setOnClickListener(listener) }
}


