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

/**
 * Reglas de PLAN2.md §4. Pura (sin Android) para poder probarla en JVM.
 *
 * Una lista vacía significa que el etiquetador falló o no está configurado: en ese caso no se
 * marcan los problemas que dependen de "no se vio nada" (no bloquear por un fallo del modelo).
 */
fun problemasPorEtiquetas(
    tipo: TipoFoto,
    etiquetas: List<EtiquetaImagen>,
    hayCodigo: Boolean,
): List<Problema> {
    if (etiquetas.isEmpty()) return emptyList()
    val problemas = mutableListOf<Problema>()
    if (tipo == TipoFoto.PRODUCTO && !hayCodigo &&
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
