package com.meteocool.utility

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.meteocool.R

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
