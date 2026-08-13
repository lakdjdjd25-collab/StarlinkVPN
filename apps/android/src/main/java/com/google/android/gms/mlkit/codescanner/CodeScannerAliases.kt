package com.google.android.gms.mlkit.codescanner

typealias GmsBarcodeScanning = com.google.mlkit.vision.codescanner.GmsBarcodeScanning

object GmsBarcodeScannerOptions {
    class Builder {
        private val delegate = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()

        fun setBarcodeFormats(format: Int, vararg additionalFormats: Int) = apply {
            delegate.setBarcodeFormats(format, *additionalFormats)
        }

        fun enableAutoZoom() = apply {
            delegate.enableAutoZoom()
        }

        fun build(): com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions = delegate.build()
    }
}
