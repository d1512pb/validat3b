package com.richi_mc.hackathon3b.feature.analysis

/** Validación del dígito verificador de códigos EAN/UPC (Kotlin puro). */
object CodigoBarras {

    /**
     * [formato] usa los nombres de ML Kit: "EAN_13", "EAN_8", "UPC_A", "UPC_E".
     * Si es null o desconocido se infiere por longitud (13, 12, 8). Un código de 8 dígitos sin
     * formato se acepta si es EAN-8 o UPC-E válido.
     */
    fun checksumValido(codigo: String, formato: String?): Boolean {
        if (codigo.isEmpty() || !codigo.all { it in '0'..'9' }) return false
        return when (formato) {
            "EAN_13" -> codigo.length == 13 && gs1Valido(codigo)
            "EAN_8" -> codigo.length == 8 && gs1Valido(codigo)
            "UPC_A" -> codigo.length == 12 && gs1Valido(codigo)
            "UPC_E" -> codigo.length == 8 && upcEValido(codigo)
            else -> when (codigo.length) {
                13, 12 -> gs1Valido(codigo)
                8 -> gs1Valido(codigo) || upcEValido(codigo)
                else -> false
            }
        }
    }

    /** Módulo 10 de GS1: pesos 3,1,3,... desde el dígito justo antes del verificador hacia la izquierda. */
    private fun gs1Valido(codigo: String): Boolean {
        val n = codigo.length
        var suma = 0
        for (i in 0 until n - 1) {
            val d = codigo[i] - '0'
            val posDesdeDerecha = n - 1 - i // el verificador es la posición 0
            suma += if (posDesdeDerecha % 2 == 1) d * 3 else d
        }
        return (10 - suma % 10) % 10 == codigo[n - 1] - '0'
    }

    /** UPC-E (8 dígitos: sistema 0/1 + 6 + verificador): expande a UPC-A y valida. */
    private fun upcEValido(codigo: String): Boolean {
        val upcA = expandirUpcE(codigo) ?: return false
        return gs1Valido(upcA)
    }

    /** Expande UPC-E de 8 dígitos a UPC-A de 12; null si el sistema numérico no es 0/1. */
    fun expandirUpcE(codigo: String): String? {
        if (codigo.length != 8 || !codigo.all { it in '0'..'9' }) return null
        val sistema = codigo[0]
        if (sistema != '0' && sistema != '1') return null
        val d = codigo.substring(1, 7)
        val verificador = codigo[7]
        val cuerpo = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}00" + "00${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00" + "000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}0" + "0000${d[4]}"
            else -> d.substring(0, 5) + "0000${d[5]}"
        }
        return "$sistema$cuerpo$verificador"
    }
}
