package com.araswqm.hachi.core

import javax.inject.Qualifier

/** Ag, dosya ve veritabani isleri icin. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU yogun isler (PNG cozme, JSON ayristirma) icin. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Uygulama omru boyunca yasayan kapsam; is mantigi ViewModel'den bagimsiz surekli isler icin. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
