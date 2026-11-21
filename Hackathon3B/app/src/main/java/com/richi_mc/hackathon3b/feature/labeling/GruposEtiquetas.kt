package com.richi_mc.hackathon3b.feature.labeling

import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoFoto

/**
 * Grupos de etiquetas del modelo base de ML Kit Image Labeling (nombres en inglés, comparación
 * sin distinguir mayúsculas). Son un punto de partida: se calibran en el teléfono con
 * `adb logcat -s Etiquetas` sobre fotos reales de tienda y se ajustan los sets según lo que salga.
 */
object GruposEtiquetas {
    const val UMBRAL_PRODUCTO = 0.6f
    const val UMBRAL_FACHADA = 0.5f
    const val UMBRAL_PANTALLA = 0.7f

    /** Texto sustancial de empaque: mínimo de caracteres alfanuméricos en total. */
    const val MIN_CARACTERES_EMPAQUE = 12

    /** Texto sustancial de empaque: mínimo de palabras "reales" (de al menos [MIN_LETRAS_PALABRA] letras). */
    const val MIN_PALABRAS_EMPAQUE = 2

    /** Una palabra cuenta solo si tiene al menos esta cantidad de letras (descarta basura del OCR). */
    const val MIN_LETRAS_PALABRA = 3

    /** Envases, alimentos, bebidas y artículos de abarrotes/cuidado personal. */
    val producto: Set<String> = setOf(
        "Bottle", "Plastic bottle", "Water bottle", "Can", "Tin can", "Drink", "Juice", "Cola",
        "Soft drink", "Beer", "Wine", "Coffee", "Tea", "Milk", "Food", "Snack", "Fast food",
        "Junk food", "Bread", "Cookie", "Cake", "Dessert", "Candy", "Chocolate", "Ice cream",
        "Fruit", "Vegetable", "Meat", "Cheese", "Egg", "Cereal", "Pasta", "Rice", "Sauce",
        "Cuisine", "Dish", "Meal", "Ingredient", "Packaging", "Packaged goods", "Box", "Carton",
        "Jar", "Cup", "Mug", "Cosmetics", "Shampoo", "Soap", "Perfume", "Toy", "Product",
    ).enMinusculas()

    /** Fachadas y entornos exteriores de tienda. */
    val fachada: Set<String> = setOf(
        "Building", "Shop", "Store", "Supermarket", "Convenience store", "Retail", "Shopping",
        "Market", "Facade", "Signage", "Sign", "Skyscraper", "House", "Tower", "Road", "Street",
        "Parking lot", "Car", "Neighbourhood", "Town", "City", "Architecture",
    ).enMinusculas()

    /** Pantallas y dispositivos electrónicos (posible foto de una pantalla). */
    val pantalla: Set<String> = setOf(
        "Monitor", "Television", "Mobile phone", "Screen", "Computer", "Laptop", "Tablet computer",
        "Display device", "Computer monitor", "Smartphone",
    ).enMinusculas()

    private fun Set<String>.enMinusculas(): Set<String> = mapTo(HashSet()) { it.lowercase() }
}

private fun List<EtiquetaImagen>.hayDelGrupo(grupo: Set<String>, umbral: Float): Boolean =
    any { it.confianza >= umbral && it.texto.lowercase() in grupo }

/** Contenido neto: "150 g", "1L", "600 ml", "2 pzas". Insensible a mayúsculas. */
private val REGEX_CONTENIDO_NETO = Regex(
    """\b\d+([.,]\d+)?\s?(g|gr|grs|kg|ml|l|lt|lts|oz|pzas?|piezas?)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Evidencia de que el OCR leyó un empaque: un contenido neto (150 g, 1 L, 600 ml...) o texto
 * sustancial (al menos [GruposEtiquetas.MIN_CARACTERES_EMPAQUE] alfanuméricos repartidos en
 * [GruposEtiquetas.MIN_PALABRAS_EMPAQUE] palabras de [GruposEtiquetas.MIN_LETRAS_PALABRA]+ letras).
 * Pura (sin Android).
 */
fun pareceTextoDeEmpaque(texto: String): Boolean {
    if (texto.isBlank()) return false
    if (REGEX_CONTENIDO_NETO.containsMatchIn(texto)) return true
    val palabras = texto.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val palabrasReales = palabras.count { p -> p.count { it.isLetter() } >= GruposEtiquetas.MIN_LETRAS_PALABRA }
    val caracteres = palabras.sumOf { it.length }
    return palabrasReales >= GruposEtiquetas.MIN_PALABRAS_EMPAQUE &&
        caracteres >= GruposEtiquetas.MIN_CARACTERES_EMPAQUE
}

/**
 * Reglas de PLAN2.md §4. Pura (sin Android) para poder probarla en JVM.
 *
 * Para PRODUCTO, [textoOcr] con evidencia de empaque ([pareceTextoDeEmpaque]) evita marcar
 * PRODUCTO_NO_VISIBLE: el etiquetador genérico ve "mano/dedo/flor" pero el OCR lee el empaque.
 *
 * Una lista vacía significa que el etiquetador falló o no está configurado: en ese caso no se
 * marcan los problemas que dependen de "no se vio nada" (no bloquear por un fallo del modelo).
 */
fun problemasPorEtiquetas(
    tipo: TipoFoto,
    etiquetas: List<EtiquetaImagen>,
    hayCodigo: Boolean,
    textoOcr: String = "",
): List<Problema> {
    if (etiquetas.isEmpty()) return emptyList()
    val problemas = mutableListOf<Problema>()
    if (tipo == TipoFoto.PRODUCTO && !hayCodigo && !pareceTextoDeEmpaque(textoOcr) &&
        !etiquetas.hayDelGrupo(GruposEtiquetas.producto, GruposEtiquetas.UMBRAL_PRODUCTO)
    ) {
        problemas += Problema.PRODUCTO_NO_VISIBLE
    }
    if (tipo == TipoFoto.FACHADA &&
        !etiquetas.hayDelGrupo(GruposEtiquetas.fachada, GruposEtiquetas.UMBRAL_FACHADA)
    ) {
        problemas += Problema.FACHADA_NO_PARECE_TIENDA
    }
    if (etiquetas.hayDelGrupo(GruposEtiquetas.pantalla, GruposEtiquetas.UMBRAL_PANTALLA)) {
        problemas += Problema.POSIBLE_FOTO_DE_PANTALLA
    }
    return problemas
}
