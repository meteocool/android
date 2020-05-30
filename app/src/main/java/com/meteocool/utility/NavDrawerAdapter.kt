package com.meteocool.utility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.meteocool.R
import com.meteocool.view.WebViewModel
import org.jetbrains.anko.colorAttr
import org.jetbrains.anko.defaultSharedPreferences


class NavDrawerAdapter(private val activity : AppCompatActivity, private val layoutResourceId : Int, private val navDrawerItems: List<NavDrawerItem>)
    : ArrayAdapter<NavDrawerItem>(activity.applicationContext, layoutResourceId, navDrawerItems) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val listItem = inflater.inflate(layoutResourceId, parent, false)
        val imageIcon = listItem.findViewById<ImageView>(R.id.drawerImgID)
        val menuHeader = listItem.findViewById<TextView>(R.id.drawerHeaderText)

        val folder = navDrawerItems[position]
        imageIcon.setImageResource(folder.imgID)

        menuHeader.text = folder.menuHeading
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            menuHeader.setTextColor(Color.WHITE)
        } else {
            menuHeader.setTextColor(Color.BLACK)
        }

        return listItem
    }


}
