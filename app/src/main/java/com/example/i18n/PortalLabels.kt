package com.example.i18n

import com.example.model.Language

object PortalLabels {

    fun getCpcbEprOversight(lang: Language): String = when (lang) {
        Language.ENGLISH -> "CPCB E-Waste EPR Oversight"
        Language.HINDI -> "सीपीसीबी ई-कचरा ईपीआर पर्यवेक्षण"
        Language.MARATHI -> "सीपीसीबी ई-कचरा ईपीआर देखरेख"
        Language.TAMIL -> "சிபிசிபி இ-கழிவு ஈபிஆர் மேற்பார்வை"
    }

    fun getMoefccSubtitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Central Pollution Control Board • MoEFCC"
        Language.HINDI -> "केंद्रीय प्रदूषण नियंत्रण बोर्ड • MoEFCC"
        Language.MARATHI -> "केंद्रीय प्रदूषण नियंत्रण मंडळ • MoEFCC"
        Language.TAMIL -> "மத்திய மாசு கட்டுப்பாட்டு வாரியம் • MoEFCC"
    }

    fun getNationalRegistry(lang: Language): String = when (lang) {
        Language.ENGLISH -> "National E-Waste Formalization Registry"
        Language.HINDI -> "राष्ट्रीय ई-कचरा औपचारिकीकरण रजिस्ट्री"
        Language.MARATHI -> "राष्ट्रीय ई-कचरा औपचारिकीकरण नोंदणी"
        Language.TAMIL -> "தேசிய இ-கழிவு முறைப்படுத்தல் பதிவேடு"
    }

    fun getAdminOfficerId(lang: Language, adminId: String): String = when (lang) {
        Language.ENGLISH -> "Admin Officer ID: $adminId (MoEFCC)"
        Language.HINDI -> "प्रशासन अधिकारी ID: $adminId (MoEFCC)"
        Language.MARATHI -> "प्रशासकीय अधिकारी ID: $adminId (MoEFCC)"
        Language.TAMIL -> "நிர்வாக அலுவலர் அடையாளம்: $adminId (MoEFCC)"
    }

    fun getInformalCollectors(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Informal Collectors"
        Language.HINDI -> "अनौपचारिक कलेक्टर"
        Language.MARATHI -> "अनौपचारिक कलेक्टर"
        Language.TAMIL -> "முறைசாரா சேகரிப்பாளர்கள்"
    }

    fun getRegisteredAccounts(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Registered accounts"
        Language.HINDI -> "पंजीकृत खाते"
        Language.MARATHI -> "नोंदणीकृत खाती"
        Language.TAMIL -> "பதிவுசெய்யப்பட்ட கணக்குகள்"
    }

    fun getFormalRecyclers(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Formal Recyclers"
        Language.HINDI -> "अधिकृत रीसायकलर"
        Language.MARATHI -> "अधिकृत रीसायकलर"
        Language.TAMIL -> "அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளர்கள்"
    }

    fun getFormalRecyclerRole(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Formal Recycler"
        Language.HINDI -> "अधिकृत रीसायकलर"
        Language.MARATHI -> "अधिकृत रीसायकलर"
        Language.TAMIL -> "அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளர்"
    }

    fun getAuthorizedFacilities(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Authorized facilities"
        Language.HINDI -> "अधिकृत सुविधाएं"
        Language.MARATHI -> "अधिकृत सुविधा"
        Language.TAMIL -> "அங்கீகரிக்கப்பட்ட வசதிகள்"
    }

    fun getRegisteredLots(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Registered Lots"
        Language.HINDI -> "पंजीकृत लॉट"
        Language.MARATHI -> "नोंदणीकृत लॉट"
        Language.TAMIL -> "பதிவுசெய்யப்பட்ட லாட்கள்"
    }

    fun getSharedCloudRecords(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Shared cloud records"
        Language.HINDI -> "साझा क्लाउड रिकॉर्ड"
        Language.MARATHI -> "सामायिक क्लाउड रेकॉर्ड"
        Language.TAMIL -> "பகிரப்பட்ட கிளவுட் பதிவுகள்"
    }

    fun getLiveEprMetrics(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Live EPR Traceability Metrics"
        Language.HINDI -> "लाइव ईपीआर ट्रेसेबिलिटी मेट्रिक्स"
        Language.MARATHI -> "लाइव ईपीआर ट्रेसेबिलिटी मेट्रिक्स"
        Language.TAMIL -> "நேரலை ஈபிஆர் கண்காணிப்பு அளவீடுகள்"
    }

    fun getAwaitingWeighIn(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Awaiting Weigh-In"
        Language.HINDI -> "वजन तोलन की प्रतीक्षा"
        Language.MARATHI -> "वजन पडताळणीची प्रतीक्षा"
        Language.TAMIL -> "எடை கணக்கீட்டிற்காக காத்திருக்கிறது"
    }

    fun getVerifiedPaid(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Verified & Paid"
        Language.HINDI -> "सत्यापित और भुगतान किया गया"
        Language.MARATHI -> "पडताळलेले आणि पैसे भरलेले"
        Language.TAMIL -> "சரிபார்க்கப்பட்டு தொகை வழங்கப்பட்டது"
    }

    fun getFormalization(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Formalization"
        Language.HINDI -> "औपचारिकीकरण"
        Language.MARATHI -> "औपचारिकीकरण"
        Language.TAMIL -> "முறைப்படுத்தல்"
    }

    fun getTraceableWeight(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Traceable Weight"
        Language.HINDI -> "ट्रेसेबल वजन"
        Language.MARATHI -> "ट्रेसेबल वजन"
        Language.TAMIL -> "கண்காணிப்பு எடை"
    }

    fun getSettledPayout(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Settled Payout"
        Language.HINDI -> "निपटाया गया भुगतान"
        Language.MARATHI -> "निपटारा केलेले पेमेंट"
        Language.TAMIL -> "செலுத்தப்பட்ட தொகை"
    }

    fun getCollectorsEmptyHint(lang: Language): String = when (lang) {
        Language.ENGLISH -> "No collector profiles visible. Sign in with the registered admin email account to load the live directory."
        Language.HINDI -> "कोई कलेक्टर प्रोफ़ाइल दिखाई नहीं दे रही। लाइव निर्देशिका लोड करने के लिए पंजीकृत प्रशासन ईमेल खाते से साइन इन करें।"
        Language.MARATHI -> "कलेक्टर प्रोफाइल दिसत नाहीत. लाइव निर्देशिका लोड करण्यासाठी नोंदणीकृत प्रशासन ईमेल खात्यावर साइन इन करा."
        Language.TAMIL -> "சேகரிப்பாளர் சுயவிவரங்கள் எதுவும் தெரியவில்லை. நேரலை கோப்பகத்தை ஏற்ற முறையான நிர்வாக மின்னஞ்சல் கணக்கில் உள்நுழைக."
    }

    fun getRecyclersEmptyHint(lang: Language): String = when (lang) {
        Language.ENGLISH -> "No recycler profiles visible. Sign in with the registered admin email account to load the live directory."
        Language.HINDI -> "कोई रीसायकलर प्रोफ़ाइल दिखाई नहीं दे रही। लाइव निर्देशिका लोड करने के लिए पंजीकृत प्रशासन ईमेल खाते से साइन इन करें।"
        Language.MARATHI -> "रीसायकलर प्रोफाइल दिसत नाहीत. लाइव निर्देशिका लोड करण्यासाठी नोंदणीकृत प्रशासन ईमेल खात्यावर साइन इन करा."
        Language.TAMIL -> "மறுசுழற்சியாளர் சுயவிவரங்கள் எதுவும் தெரியவில்லை. நேரலை கோப்பகத்தை ஏற்ற முறையான நிர்வாக மின்னஞ்சல் கணக்கில் உள்நுழைக."
    }

    fun getDeviceLotsAwaitingLink(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Device Lots Awaiting Profile Link"
        Language.HINDI -> "प्रोफाइल लिंक की प्रतीक्षा कर रहे डिवाइस लॉट"
        Language.MARATHI -> "प्रोफाइल लिंकची प्रतीक्षा असलेले डिव्हाइस लॉट"
        Language.TAMIL -> "சுயவிவர இணைப்பிற்காக காத்திருக்கும் சாதன லாட்கள்"
    }

    fun getSharedTraceabilityRecords(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Shared Traceability Records"
        Language.HINDI -> "साझा ट्रेसेबिलिटी रिकॉर्ड"
        Language.MARATHI -> "सामायिक ट्रेसेबिलिटी रेकॉर्ड"
        Language.TAMIL -> "பகிரப்பட்ட கண்காணிப்பு பதிவுகள்"
    }

    fun getNoTraceabilityRecords(lang: Language): String = when (lang) {
        Language.ENGLISH -> "No traceability records yet. A lot created by the informal collector and confirmed by the formal recycler appears here automatically."
        Language.HINDI -> "अभी तक कोई ट्रेसेबिलिटी रिकॉर्ड नहीं। अनौपचारिक कलेक्टर द्वारा बनाया गया और अधिकृत रीसायकलर द्वारा पुष्टि किया गया लॉट यहाँ स्वचालित रूप से दिखाई देता है।"
        Language.MARATHI -> "अजून कोणतेही ट्रेसेबिलिटी रेकॉर्ड नाहीत. अनौपचारिक कलेक्टरने तयार केलेला आणि अधिकृत रीसायकलरने पुष्टी केलेला लॉट येथे आपोआप दिसतो."
        Language.TAMIL -> "இன்னும் கண்காணிப்பு பதிவுகள் இல்லை. முறைசாரா சேகரிப்பாளரால் உருவாக்கப்பட்டு அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளரால் உறுதிசெய்யப்படும் லாட் இங்கே தானாக தோன்றும்."
    }

    fun getCollectorLine(lang: Language, name: String): String = when (lang) {
        Language.ENGLISH -> "Collector: $name"
        Language.HINDI -> "कलेक्टर: $name"
        Language.MARATHI -> "कलेक्टर: $name"
        Language.TAMIL -> "சேகரிப்பாளர்: $name"
    }

    fun getLocationLine(lang: Language, location: String): String = when (lang) {
        Language.ENGLISH -> "Location: $location"
        Language.HINDI -> "स्थान: $location"
        Language.MARATHI -> "स्थान: $location"
        Language.TAMIL -> "இடம்: $location"
    }

    fun getValueLine(lang: Language, value: Int): String = when (lang) {
        Language.ENGLISH -> "Value: ₹$value"
        Language.HINDI -> "मूल्य: ₹$value"
        Language.MARATHI -> "मूल्य: ₹$value"
        Language.TAMIL -> "மதிப்பு: ₹$value"
    }

    fun getLiveCount(lang: Language, count: Int): String = when (lang) {
        Language.ENGLISH -> "$count live"
        Language.HINDI -> "$count लाइव"
        Language.MARATHI -> "$count लाइव"
        Language.TAMIL -> "$count நேரலை"
    }

    fun getActiveStatus(lang: Language): String = when (lang) {
        Language.ENGLISH -> "active"
        Language.HINDI -> "सक्रिय"
        Language.MARATHI -> "सक्रिय"
        Language.TAMIL -> "செயலில்"
    }

    fun getLotsCount(lang: Language, count: Int): String = when (lang) {
        Language.ENGLISH -> "$count lots"
        Language.HINDI -> "$count लॉट"
        Language.MARATHI -> "$count लॉट"
        Language.TAMIL -> "$count லாட்கள்"
    }

    fun getLotsSummary(lang: Language, count: Int, kgText: String, valueInr: Int): String = when (lang) {
        Language.ENGLISH -> "$count lots • $kgText kg • ₹$valueInr"
        Language.HINDI -> "$count लॉट • $kgText किग्रा • ₹$valueInr"
        Language.MARATHI -> "$count लॉट • $kgText किलो • ₹$valueInr"
        Language.TAMIL -> "$count லாட்கள் • $kgText கிலோ • ₹$valueInr"
    }

    fun getAdminStatusBadge(lang: Language, verified: Boolean): String = when (lang) {
        Language.ENGLISH -> if (verified) "VERIFIED & PAID" else "AWAITING WEIGH-IN"
        Language.HINDI -> if (verified) "सत्यापित और भुगतान किया गया" else "वजन तोलन की प्रतीक्षा"
        Language.MARATHI -> if (verified) "पडताळलेले आणि पैसे भरलेले" else "वजन पडताळणीची प्रतीक्षा"
        Language.TAMIL -> if (verified) "சரிபார்க்கப்பட்டு தொகை வழங்கப்பட்டது" else "எடை கணக்கீட்டிற்காக காத்திருக்கிறது"
    }

    fun getRecyclerStatusBadge(lang: Language, verified: Boolean): String = when (lang) {
        Language.ENGLISH -> if (verified) "VERIFIED" else "AWAITING WEIGH-IN"
        Language.HINDI -> if (verified) "सत्यापित" else "वजन तोलन की प्रतीक्षा"
        Language.MARATHI -> if (verified) "पडताळलेले" else "वजन पडताळणीची प्रतीक्षा"
        Language.TAMIL -> if (verified) "சரிபார்க்கப்பட்டது" else "எடை கணக்கீட்டிற்காக காத்திருக்கிறது"
    }

    fun getRecyclerInboundWeighInTitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Recycler Inbound Weigh-In"
        Language.HINDI -> "रीसायकलर इनबाउंड वजन तोलन"
        Language.MARATHI -> "रीसायकलर इनबाउंड वजन पडताळणी"
        Language.TAMIL -> "மறுசுழற்சியாளர் உள்வரும் எடை கணக்கீடு"
    }

    fun getCpcbAuthenticated(lang: Language): String = when (lang) {
        Language.ENGLISH -> "CPCB Authenticated"
        Language.HINDI -> "सीपीसीबी प्रमाणित"
        Language.MARATHI -> "सीपीसीबी प्रमाणित"
        Language.TAMIL -> "சிபிசிபி சான்றளிக்கப்பட்டது"
    }

    fun getDashboardTab(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Dashboard"
        Language.HINDI -> "डैशबोर्ड"
        Language.MARATHI -> "डॅशबोर्ड"
        Language.TAMIL -> "டாஷ்போர்டு"
    }

    fun getInboundLotsTab(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Inbound Lots"
        Language.HINDI -> "इनबाउंड लॉट"
        Language.MARATHI -> "इनबाउंड लॉट"
        Language.TAMIL -> "உள்வரும் லாட்கள்"
    }

    fun getWeighInTab(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Weigh-in"
        Language.HINDI -> "वजन तोलन"
        Language.MARATHI -> "वजन पडताळणी"
        Language.TAMIL -> "எடை கணக்கீடு"
    }

    fun getReportsTab(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Reports"
        Language.HINDI -> "रिपोर्ट"
        Language.MARATHI -> "अहवाल"
        Language.TAMIL -> "அறிக்கைகள்"
    }

    fun getProfileTab(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Profile"
        Language.HINDI -> "प्रोफ़ाइल"
        Language.MARATHI -> "प्रोफाइल"
        Language.TAMIL -> "சுயவிவரம்"
    }

    fun getRecyclerDashboard(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Recycler Dashboard"
        Language.HINDI -> "रीसायकलर डैशबोर्ड"
        Language.MARATHI -> "रीसायकलर डॅशबोर्ड"
        Language.TAMIL -> "மறுசுழற்சியாளர் டாஷ்போர்டு"
    }

    fun getCpcbRegisteredRecycler(lang: Language): String = when (lang) {
        Language.ENGLISH -> "CPCB Registered Recycler"
        Language.HINDI -> "सीपीसीबी पंजीकृत रीसायकलर"
        Language.MARATHI -> "सीपीसीबी नोंदणीकृत रीसायकलर"
        Language.TAMIL -> "சிபிசிபி பதிவுசெய்யப்பட்ட மறுசுழற்சியாளர்"
    }

    fun getCpcbIdLine(lang: Language, cpcbNumber: String): String = when (lang) {
        Language.ENGLISH -> "CPCB ID: $cpcbNumber"
        Language.HINDI -> "सीपीसीबी ID: $cpcbNumber"
        Language.MARATHI -> "सीपीसीबी ID: $cpcbNumber"
        Language.TAMIL -> "சிபிசிபி அடையாளம்: $cpcbNumber"
    }

    fun getOnlineStatus(lang: Language, isSyncing: Boolean): String = when (lang) {
        Language.ENGLISH -> if (isSyncing) "System Active • syncing" else "Online • System Active"
        Language.HINDI -> if (isSyncing) "सिस्टम सक्रिय • सिंक हो रहा है" else "ऑनलाइन • सिस्टम सक्रिय"
        Language.MARATHI -> if (isSyncing) "सिस्टम सक्रिय • सिंक होत आहे" else "ऑनलाइन • सिस्टम सक्रिय"
        Language.TAMIL -> if (isSyncing) "அமைப்பு செயலில் • ஒத்திசைவில் உள்ளது" else "ஆன்லைன் • அமைப்பு செயலில்"
    }

    fun getActiveInboundLots(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Active Inbound Lots"
        Language.HINDI -> "सक्रिय इनबाउंड लॉट"
        Language.MARATHI -> "सक्रिय इनबाउंड लॉट"
        Language.TAMIL -> "செயலில் உள்ள உள்வரும் லாட்கள்"
    }

    fun getEprCreditsAccrued(lang: Language): String = when (lang) {
        Language.ENGLISH -> "EPR Credits Accrued"
        Language.HINDI -> "अर्जित ईपीआर क्रेडिट"
        Language.MARATHI -> "जमा झालेले ईपीआर क्रेडिट"
        Language.TAMIL -> "திரட்டப்பட்ட ஈபிஆர் கிரெடிட்கள்"
    }

    fun getVerifiedWeightSubtitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Verified weight"
        Language.HINDI -> "सत्यापित वजन"
        Language.MARATHI -> "पडताळलेले वजन"
        Language.TAMIL -> "சரிபார்க்கப்பட்ட எடை"
    }

    fun getTotalWeightReceived(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Total Weight Received"
        Language.HINDI -> "कुल प्राप्त वजन"
        Language.MARATHI -> "एकूण प्राप्त वजन"
        Language.TAMIL -> "பெறப்பட்ட மொத்த எடை"
    }

    fun getAllLotsSubtitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "All lots"
        Language.HINDI -> "सभी लॉट"
        Language.MARATHI -> "सर्व लॉट"
        Language.TAMIL -> "அனைத்து லாட்கள்"
    }

    fun getTotalCollectors(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Total Collectors"
        Language.HINDI -> "कुल कलेक्टर"
        Language.MARATHI -> "एकूण कलेक्टर"
        Language.TAMIL -> "மொத்த சேகரிப்பாளர்கள்"
    }

    fun getPartnerCollectors(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Partner Collectors"
        Language.HINDI -> "साथी कलेक्टर"
        Language.MARATHI -> "भागीदार कलेक्टर"
        Language.TAMIL -> "கூட்டாளர் சேகரிப்பாளர்கள்"
    }

    fun getScanCollectorHandoverQr(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Scan Collector Handover QR"
        Language.HINDI -> "कलेक्टर हैंडओवर QR स्कैन करें"
        Language.MARATHI -> "कलेक्टर हँडओव्हर QR स्कॅन करा"
        Language.TAMIL -> "சேகரிப்பாளர் ஒப்படைப்பு QR ஐ ஸ்கேன் செய்க"
    }

    fun getInstantScaleSync(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Instant digital scale sync & automatic EPR credit logging"
        Language.HINDI -> "तुरंत डिजिटल स्केल सिंक और स्वचालित ईपीआर क्रेडिट लॉगिंग"
        Language.MARATHI -> "तत्काळ डिजिटल स्केल सिंक आणि स्वयंचलित ईपीआर क्रेडिट लॉगिंग"
        Language.TAMIL -> "உடனடி டிஜிட்டல் எடை ஒத்திசைவு மற்றும் தானியங்கி ஈபிஆர் கிரெடிட் பதிவு"
    }

    fun getManualWeighIn(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Manual Weigh-In"
        Language.HINDI -> "मैन्युअल वजन तोलन"
        Language.MARATHI -> "मॅन्युअल वजन पडताळणी"
        Language.TAMIL -> "கைமுறை எடை கணக்கீடு"
    }

    fun getEnterLotDetailsManually(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Enter lot details manually"
        Language.HINDI -> "लॉट विवरण मैन्युअल रूप से दर्ज करें"
        Language.MARATHI -> "लॉट तपशील मॅन्युअली नोंदवा"
        Language.TAMIL -> "லாட் விவரங்களை கைமுறையாக உள்ளிடுக"
    }

    fun getCollectorLotsAwaitingScaleWeighIn(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Collector Lots Awaiting Scale Weigh-In"
        Language.HINDI -> "वजन तोलन की प्रतीक्षा कर रहे कलेक्टर लॉट"
        Language.MARATHI -> "वजन पडताळणीची प्रतीक्षा असलेले कलेक्टर लॉट"
        Language.TAMIL -> "எடை கணக்கீட்டிற்காக காத்திருக்கும் சேகரிப்பாளர் லாட்கள்"
    }

    fun getFilter(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Filter"
        Language.HINDI -> "फ़िल्टर"
        Language.MARATHI -> "फिल्टर"
        Language.TAMIL -> "வடிகட்டு"
    }

    fun getFilterAll(lang: Language): String = when (lang) {
        Language.ENGLISH -> "All"
        Language.HINDI -> "सभी"
        Language.MARATHI -> "सर्व"
        Language.TAMIL -> "அனைத்தும்"
    }

    fun getFilterVerified(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Verified"
        Language.HINDI -> "सत्यापित"
        Language.MARATHI -> "पडताळलेले"
        Language.TAMIL -> "சரிபார்க்கப்பட்டது"
    }

    fun getNoInboundLots(lang: Language): String = when (lang) {
        Language.ENGLISH -> "No inbound lots yet. A record created by the informal collector appears here automatically after the next refresh."
        Language.HINDI -> "अभी तक कोई इनबाउंड लॉट नहीं। अनौपचारिक कलेक्टर द्वारा बनाया गया रिकॉर्ड अगली ताज़गी के बाद यहाँ स्वचालित रूप से दिखाई देता है।"
        Language.MARATHI -> "अजून इनबाउंड लॉट नाहीत. अनौपचारिक कलेक्टरने तयार केलेला रेकॉर्ड पुढील रिफ्रेशनंतर येथे आपोआप दिसतो."
        Language.TAMIL -> "இன்னும் உள்வரும் லாட்கள் இல்லை. முறைசாரா சேகரிப்பாளரால் உருவாக்கப்படும் பதிவு அடுத்த புதுப்பிப்பிற்குப் பிறகு இங்கே தானாக தோன்றும்."
    }

    fun getCollectionPointLine(lang: Language, location: String): String = when (lang) {
        Language.ENGLISH -> "Collection Point: $location"
        Language.HINDI -> "संग्रह बिंदु: $location"
        Language.MARATHI -> "संकलन बिंदू: $location"
        Language.TAMIL -> "சேகரிப்பு மையம்: $location"
    }

    fun getManuallyEnteredWeightLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Manually entered weight at scale (kg)"
        Language.HINDI -> "स्केल पर मैन्युअल दर्ज किया गया वजन (किग्रा)"
        Language.MARATHI -> "स्केलवर मॅन्युअली नोंदवलेले वजन (किलो)"
        Language.TAMIL -> "எடைக் கருவியில் கைமுறையாக உள்ளிட்ட எடை (கிலோ)"
    }

    fun getVerifyWeightAtScale(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Verify Weight at Scale"
        Language.HINDI -> "स्केल पर वजन सत्यापित करें"
        Language.MARATHI -> "स्केलवर वजन पडताळा"
        Language.TAMIL -> "எடைக் கருவியில் எடையை சரிபார்க்கவும்"
    }

    fun getEprCertificateIssued(lang: Language, certNo: String): String = when (lang) {
        Language.ENGLISH -> "EPR Traceability Certificate Issued ($certNo)"
        Language.HINDI -> "ईपीआर ट्रेसेबिलिटी प्रमाणपत्र जारी ($certNo)"
        Language.MARATHI -> "ईपीआर ट्रेसेबिलिटी प्रमाणपत्र जारी ($certNo)"
        Language.TAMIL -> "ஈபிஆர் கண்காணிப்பு சான்றிதழ் வழங்கப்பட்டது ($certNo)"
    }

    fun getInboundSummary(lang: Language, pending: Int, total: Int): String = when (lang) {
        Language.ENGLISH -> "$pending awaiting weigh-in • $total total inbound"
        Language.HINDI -> "$pending वजन तोलन की प्रतीक्षा • $total कुल इनबाउंड"
        Language.MARATHI -> "$pending वजन पडताळणीची प्रतीक्षा • $total एकूण इनबाउंड"
        Language.TAMIL -> "$pending எடை கணக்கீட்டிற்காக காத்திருக்கிறது • $total மொத்த உள்வரும்"
    }

    fun getGoToWeighIn(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Go to Weigh-In"
        Language.HINDI -> "वजन तोलन पर जाएं"
        Language.MARATHI -> "वजन पडताळणीवर जा"
        Language.TAMIL -> "எடை கணக்கீட்டிற்கு செல்க"
    }

    fun getFillForm6Manifest(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Fill Form-6 Manifest"
        Language.HINDI -> "फॉर्म-6 मैनिफेस्ट भरें"
        Language.MARATHI -> "फॉर्म-6 मॅनिफेस्ट भरा"
        Language.TAMIL -> "ஃபார்ம்-6 மேனிஃபெஸ்டை பூர்த்தி செய்க"
    }

    fun getViewUpdateForm6(lang: Language): String = when (lang) {
        Language.ENGLISH -> "View / Update Form-6"
        Language.HINDI -> "फॉर्म-6 देखें / अपडेट करें"
        Language.MARATHI -> "फॉर्म-6 पहा / अपडेट करा"
        Language.TAMIL -> "ஃபார்ம்-6 பார்க்க / புதுப்பிக்க"
    }

    fun getManifestTitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "E-Waste Manifest — Form 6"
        Language.HINDI -> "ई-कचरा मैनिफेस्ट — फॉर्म 6"
        Language.MARATHI -> "ई-कचरा मॅनिफेस्ट — फॉर्म 6"
        Language.TAMIL -> "இ-கழிவு மேனிஃபெஸ்ட் — ஃபார்ம் 6"
    }

    fun getManifestLotLine(lang: Language, lotId: String, title: String, kg: Double): String = when (lang) {
        Language.ENGLISH -> "Lot $lotId • $title • $kg kg"
        Language.HINDI -> "लॉट $lotId • $title • $kg किग्रा"
        Language.MARATHI -> "लॉट $lotId • $title • $kg किलो"
        Language.TAMIL -> "லாட் $lotId • $title • $kg கிலோ"
    }

    fun getSenderAddressLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Sender name & address"
        Language.HINDI -> "प्रेषक का नाम और पता"
        Language.MARATHI -> "पाठवणार्याचे नाव आणि पत्ता"
        Language.TAMIL -> "அனுப்புநரின் பெயர் மற்றும் முகவரி"
    }

    fun getTransporterAddressLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Transporter name & address"
        Language.HINDI -> "परिवहनकर्ता का नाम और पता"
        Language.MARATHI -> "वाहतूकदाराचे नाव आणि पत्ता"
        Language.TAMIL -> "கடத்துநரின் பெயர் மற்றும் முகவரி"
    }

    fun getVehicleLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Vehicle type / registration number"
        Language.HINDI -> "वाहन का प्रकार / पंजीकरण संख्या"
        Language.MARATHI -> "वाहनाचा प्रकार / नोंदणी क्रमांक"
        Language.TAMIL -> "வாகன வகை / பதிவு எண்"
    }

    fun getReceiverLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Receiver / recycler name & address"
        Language.HINDI -> "प्राप्तकर्ता / रीसायकलर का नाम और पता"
        Language.MARATHI -> "प्राप्तकर्ता / रीसायकलरचे नाव आणि पत्ता"
        Language.TAMIL -> "பெறுநர் / மறுசுழற்சியாளரின் பெயர் மற்றும் முகவரி"
    }

    fun getReceiverAuthLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Receiver authorization number"
        Language.HINDI -> "प्राप्तकर्ता प्राधिकरण संख्या"
        Language.MARATHI -> "प्राप्तकर्ता अधिकृतता क्रमांक"
        Language.TAMIL -> "பெறுநர் அங்கீகார எண்"
    }

    fun getHandoverNotesLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Handover notes"
        Language.HINDI -> "हैंडओवर नोट्स"
        Language.MARATHI -> "हँडओव्हर नोट्स"
        Language.TAMIL -> "ஒப்படைப்பு குறிப்புகள்"
    }

    fun getSaveShareWithCollector(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Save & share with collector"
        Language.HINDI -> "सहेजें और कलेक्टर के साथ साझा करें"
        Language.MARATHI -> "जतन करा आणि कलेक्टरसोबत शेअर करा"
        Language.TAMIL -> "சேமித்து சேகரிப்பாளருடன் பகிரவும்"
    }

    fun getScaleWeighInVerification(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Scale Weigh-In & Verification"
        Language.HINDI -> "स्केल वजन तोलन और सत्यापन"
        Language.MARATHI -> "स्केल वजन पडताळणी आणि सत्यापन"
        Language.TAMIL -> "எடைக் கணக்கீடு மற்றும் சரிபார்ப்பு"
    }

    fun getWeighInSummary(lang: Language, pending: Int): String = when (lang) {
        Language.ENGLISH -> "$pending lots awaiting verified scale weight"
        Language.HINDI -> "$pending लॉट सत्यापित स्केल वजन की प्रतीक्षा में"
        Language.MARATHI -> "$pending लॉट पडताळलेल्या स्केल वजनाची प्रतीक्षेत"
        Language.TAMIL -> "$pending லாட்கள் சரிபார்க்கப்பட்ட எடைக்காக காத்திருக்கிறது"
    }

    fun getAllWeightVerifiedEmpty(lang: Language): String = when (lang) {
        Language.ENGLISH -> "All inbound lots are weight-verified. No pending weigh-ins."
        Language.HINDI -> "सभी इनबाउंड लॉट वजन-सत्यापित हैं। कोई लंबित वजन तोलन नहीं।"
        Language.MARATHI -> "सर्व इनबाउंड लॉट वजन-पडताळलेले आहेत. कोणतेही प्रलंबित वजन पडताळणी नाही."
        Language.TAMIL -> "அனைத்து உள்வரும் லாட்களும் எடை சரிபார்க்கப்பட்டவை. நிலுவையில் உள்ள எடை கணக்கீடுகள் இல்லை."
    }

    fun getEprWeightReports(lang: Language): String = when (lang) {
        Language.ENGLISH -> "EPR & Weight Reports"
        Language.HINDI -> "ईपीआर और वजन रिपोर्ट"
        Language.MARATHI -> "ईपीआर आणि वजन अहवाल"
        Language.TAMIL -> "ஈபிஆர் மற்றும் எடை அறிக்கைகள்"
    }

    fun getAutoGeneratedReports(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Auto-generated from verified weigh-in records"
        Language.HINDI -> "सत्यापित वजन तोलन रिकॉर्ड से स्वचालित रूप से उत्पन्न"
        Language.MARATHI -> "पडताळलेल्या वजन पडताळणी रेकॉर्डवरून स्वयंचलितपणे तयार"
        Language.TAMIL -> "சரிபார்க்கப்பட்ட எடை கணக்கீட்டு பதிவுகளிலிருந்து தானாக உருவாக்கப்பட்டது"
    }

    fun getTotalInbound(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Total Inbound"
        Language.HINDI -> "कुल इनबाउंड"
        Language.MARATHI -> "एकूण इनबाउंड"
        Language.TAMIL -> "மொத்த உள்வரும்"
    }

    fun getLotsSubtitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Lots"
        Language.HINDI -> "लॉट"
        Language.MARATHI -> "लॉट"
        Language.TAMIL -> "லாட்கள்"
    }

    fun getWeightVerified(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Weight Verified"
        Language.HINDI -> "वजन सत्यापित"
        Language.MARATHI -> "वजन पडताळलेले"
        Language.TAMIL -> "சரிபார்க்கப்பட்ட எடை"
    }

    fun getPendingLotsSubtitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Pending lots"
        Language.HINDI -> "लंबित लॉट"
        Language.MARATHI -> "प्रलंबित लॉट"
        Language.TAMIL -> "நிலுவையில் உள்ள லாட்கள்"
    }

    fun getNoEprCertificatesYet(lang: Language): String = when (lang) {
        Language.ENGLISH -> "No EPR traceability certificates issued yet. Complete a weigh-in to generate the first certificate."
        Language.HINDI -> "अभी तक कोई ईपीआर ट्रेसेबिलिटी प्रमाणपत्र जारी नहीं किया गया। पहला प्रमाणपत्र बनाने के लिए वजन तोलन पूर्ण करें।"
        Language.MARATHI -> "अजून कोणतेही ईपीआर ट्रेसेबिलिटी प्रमाणपत्र जारी केलेले नाही. पहिले प्रमाणपत्र तयार करण्यासाठी वजन पडताळणी पूर्ण करा."
        Language.TAMIL -> "இன்னும் ஈபிஆர் கண்காணிப்பு சான்றிதழ்கள் வழங்கப்படவில்லை. முதல் சான்றிதழை உருவாக்க எடை கணக்கீட்டை முடிக்கவும்."
    }

    fun getEprCertificateLabel(lang: Language, certNo: String): String = when (lang) {
        Language.ENGLISH -> "EPR Traceability Certificate ($certNo)"
        Language.HINDI -> "ईपीआर ट्रेसेबिलिटी प्रमाणपत्र ($certNo)"
        Language.MARATHI -> "ईपीआर ट्रेसेबिलिटी प्रमाणपत्र ($certNo)"
        Language.TAMIL -> "ஈபிஆர் கண்காணிப்பு சான்றிதழ் ($certNo)"
    }

    fun getFacilityNameLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Facility Name"
        Language.HINDI -> "सुविधा का नाम"
        Language.MARATHI -> "सुविधेचे नाव"
        Language.TAMIL -> "வசதி பெயர்"
    }

    fun getCpcbIdLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "CPCB ID"
        Language.HINDI -> "सीपीसीबी ID"
        Language.MARATHI -> "सीपीसीबी ID"
        Language.TAMIL -> "சிபிசிபி அடையாளம்"
    }

    fun getRoleLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Role"
        Language.HINDI -> "भूमिका"
        Language.MARATHI -> "भूमिका"
        Language.TAMIL -> "பங்கு"
    }

    fun getOfflineFirstNote(lang: Language): String = when (lang) {
        Language.ENGLISH -> "All inbound-queue, weigh-in and EPR records are stored offline-first on this device and optionally pushed to the CPCB platform."
        Language.HINDI -> "सभी इनबाउंड-कतार, वजन तोलन और ईपीआर रिकॉर्ड इस डिवाइस पर पहले ऑफ़लाइन संग्रहीत किए जाते हैं और वैकल्पिक रूप से सीपीसीबी प्लेटफ़ॉर्म पर भेजे जाते हैं।"
        Language.MARATHI -> "सर्व इनबाउंड-रांग, वजन पडताळणी आणि ईपीआर रेकॉर्ड या डिव्हाइसवर प्रथम ऑफलाइन संग्रहित केले जातात आणि पर्यायाने सीपीसीबी प्लॅटफॉर्मवर पाठवले जातात."
        Language.TAMIL -> "அனைத்து உள்வரும் வரிசை, எடை கணக்கீடு மற்றும் ஈபிஆர் பதிவுகள் இந்த சாதனத்தில் முதலில் ஆஃப்லைனில் சேமிக்கப்பட்டு, விருப்பப்படி சிபிசிபி தளத்திற்கு அனுப்பப்படும்."
    }

    fun getSignOut(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Sign Out"
        Language.HINDI -> "साइन आउट"
        Language.MARATHI -> "साइन आउट"
        Language.TAMIL -> "வெளியேறு"
    }

    fun getHandoverCodesFromReceipts(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Handover codes from collector digital receipts"
        Language.HINDI -> "कलेक्टर डिजिटल रसीदों से हैंडओवर कोड"
        Language.MARATHI -> "कलेक्टर डिजिटल पावत्यांमधून हँडओव्हर कोड"
        Language.TAMIL -> "சேகரிப்பாளர் டிஜிட்டல் ரசீதுகளிலிருந்து ஒப்படைப்பு குறியீடுகள்"
    }

    fun getNoLotsAwaitingHandover(lang: Language): String = when (lang) {
        Language.ENGLISH -> "No lots awaiting handover. Records from informal collectors appear automatically after refresh."
        Language.HINDI -> "हैंडओवर की प्रतीक्षा कर रहे कोई लॉट नहीं। ताज़गी के बाद अनौपचारिक कलेक्टरों के रिकॉर्ड स्वचालित रूप से दिखाई देते हैं।"
        Language.MARATHI -> "हँडओव्हरची प्रतीक्षा करणारे कोणतेही लॉट नाहीत. रिफ्रेशनंतर अनौपचारिक कलेक्टरांचे रेकॉर्ड आपोआप दिसतात."
        Language.TAMIL -> "ஒப்படைப்புக்காக காத்திருக்கும் லாட்கள் இல்லை. புதுப்பித்த பிறகு முறைசாரா சேகரிப்பாளர்களின் பதிவுகள் தானாக தோன்றும்."
    }

    fun getCodeLine(lang: Language, code: String): String = when (lang) {
        Language.ENGLISH -> "Code: $code"
        Language.HINDI -> "कोड: $code"
        Language.MARATHI -> "कोड: $code"
        Language.TAMIL -> "குறியீடு: $code"
    }

    fun getScanAndPay(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Scan & Pay"
        Language.HINDI -> "स्कैन और भुगतान करें"
        Language.MARATHI -> "स्कॅन आणि पैसे द्या"
        Language.TAMIL -> "ஸ்கேன் செய்து பணம் செலுத்து"
    }

    fun getSelectLot(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Select lot"
        Language.HINDI -> "लॉट चुनें"
        Language.MARATHI -> "लॉट निवडा"
        Language.TAMIL -> "லாட் தேர்ந்தெடுக்கவும்"
    }

    fun getWeightAtScaleLabel(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Weight at scale (kg)"
        Language.HINDI -> "स्केल पर वजन (किग्रा)"
        Language.MARATHI -> "स्केलवर वजन (किलो)"
        Language.TAMIL -> "எடைக் கருவியில் எடை (கிலோ)"
    }

    fun getVerifyWeight(lang: Language): String = when (lang) {
        Language.ENGLISH -> "Verify Weight"
        Language.HINDI -> "वजन सत्यापित करें"
        Language.MARATHI -> "वजन पडताळा"
        Language.TAMIL -> "எடையை சரிபார்க்கவும்"
    }
}