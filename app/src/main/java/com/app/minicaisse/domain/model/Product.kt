package com.app.minicaisse.domain.model

data class Product(
    val id: Int,
    val name: String,
    val price: Double
)

// Les 8 produits en dur demandés
object Catalog {
    val products = listOf(
        Product(1, "Café", 1.50),
        Product(2, "Thé", 1.80),
        Product(3, "Croissant", 1.20),
        Product(4, "Sandwich", 4.50),
        Product(5, "Eau (50cl)", 1.00),
        Product(6, "Soda (33cl)", 2.00),
        Product(7, "Jus d'orange", 2.50),
        Product(8, "Cookie", 1.50)
    )
}