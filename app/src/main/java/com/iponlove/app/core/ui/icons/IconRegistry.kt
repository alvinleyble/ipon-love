package com.iponlove.app.core.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Stable string keys → ImageVectors for transaction categories. */
val CATEGORY_ICONS: Map<String, ImageVector> = linkedMapOf(
    "food" to Icons.Filled.Restaurant,
    "fastfood" to Icons.Filled.Fastfood,
    "coffee" to Icons.Filled.LocalCafe,
    "groceries" to Icons.Filled.ShoppingCart,
    "shopping" to Icons.Filled.ShoppingBag,
    "transport" to Icons.Filled.DirectionsCar,
    "commute" to Icons.Filled.Commute,
    "fuel" to Icons.Filled.LocalGasStation,
    "home" to Icons.Filled.Home,
    "rent" to Icons.Filled.Apartment,
    "utilities" to Icons.Filled.ElectricBolt,
    "water" to Icons.Filled.WaterDrop,
    "wifi" to Icons.Filled.Wifi,
    "health" to Icons.Filled.LocalHospital,
    "medicine" to Icons.Filled.Medication,
    "fitness" to Icons.Filled.FitnessCenter,
    "education" to Icons.Filled.School,
    "book" to Icons.Filled.MenuBook,
    "entertainment" to Icons.Filled.Movie,
    "gaming" to Icons.Filled.SportsEsports,
    "music" to Icons.Filled.MusicNote,
    "travel" to Icons.Filled.Flight,
    "hotel" to Icons.Filled.Hotel,
    "salary" to Icons.Filled.AccountBalanceWallet,
    "payment" to Icons.Filled.Payments,
    "business" to Icons.Filled.Business,
    "work" to Icons.Filled.Work,
    "bills" to Icons.Filled.Receipt,
    "savings" to Icons.Filled.Savings,
    "gift" to Icons.Filled.CardGiftcard,
    "baby" to Icons.Filled.ChildCare,
    "pet" to Icons.Filled.Pets,
    "phone" to Icons.Filled.PhoneAndroid,
    "clothing" to Icons.Filled.Checkroom,
    "beauty" to Icons.Filled.Face,
    "star" to Icons.Filled.Star,
)

/** Stable string keys → ImageVectors for accounts. */
val ACCOUNT_ICONS: Map<String, ImageVector> = linkedMapOf(
    "cash" to Icons.Filled.Payments,
    "wallet" to Icons.Filled.AccountBalanceWallet,
    "bank" to Icons.Filled.AccountBalance,
    "savings" to Icons.Filled.Savings,
    "card" to Icons.Filled.CreditCard,
    "ewallet" to Icons.Filled.PhoneAndroid,
    "atm" to Icons.Filled.LocalAtm,
    "investment" to Icons.Filled.TrendingUp,
    "loan" to Icons.Filled.RequestQuote,
    "business" to Icons.Filled.Business,
    "stock" to Icons.Filled.ShowChart,
    "crypto" to Icons.Filled.CurrencyBitcoin,
    "travel" to Icons.Filled.Flight,
    "home" to Icons.Filled.Home,
    "star" to Icons.Filled.Star,
)
