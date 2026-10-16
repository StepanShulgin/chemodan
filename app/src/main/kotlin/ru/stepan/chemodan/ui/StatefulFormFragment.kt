package ru.stepan.chemodan.ui

import android.os.Bundle
import android.text.InputType
import androidx.fragment.app.Fragment

internal const val DECIMAL = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

abstract class StatefulFormFragment : Fragment() {
    protected var draft = Bundle()
    protected var capture: (() -> Bundle)? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        draft = state?.getBundle("draft") ?: Bundle()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("draft", capture?.invoke() ?: draft)
    }

    override fun onDestroyView() {
        draft = capture?.invoke() ?: draft
        capture = null
        super.onDestroyView()
    }
}
