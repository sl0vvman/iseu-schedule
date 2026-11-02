package by.iseu.schedule.data.network

import java.io.IOException

class SiteHttpException(val status: Int) : IOException("HTTP $status")
