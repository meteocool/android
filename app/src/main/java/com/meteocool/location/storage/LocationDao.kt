package com.meteocool.location.storage

import androidx.room.*
import com.meteocool.location.MeteocoolLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLocation(location: MeteocoolLocation)

    @Update
    fun updateLocation(location: MeteocoolLocation)

    @Query("SELECT * FROM MeteocoolLocation LIMIT 1")
    fun getLastLocation() : Flow<MeteocoolLocation>

    @Query("SELECT EXISTS(SELECT * FROM MeteocoolLocation)")
    fun isExists() : Boolean

}