package io.github.artcraftandroid.wordcraft

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

/** Prints a finished PDF (the engine's export of the document) through the phone's print service. */
class PdfPrintAdapter(private val name: String, private val pdf: ByteArray) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
        try {
            FileOutputStream(destination.fileDescriptor).use { it.write(pdf) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (t: Exception) {
            callback.onWriteFailed(t.message)
        }
    }
}
