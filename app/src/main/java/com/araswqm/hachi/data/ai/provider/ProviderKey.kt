package com.araswqm.hachi.data.ai.provider

import dagger.MapKey

/**
 * §6 coklu baglama anahtari: `@IntoMap` saglayicilari `Map<ProviderId, AiProvider>`
 * olarak birlesir. Dagger enum uyeli anahtarlari dogrudan destekler.
 */
@MapKey
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class ProviderKey(val value: ProviderId)
