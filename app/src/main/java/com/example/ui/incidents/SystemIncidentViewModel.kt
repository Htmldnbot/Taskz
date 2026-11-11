package com.example.ui.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.SystemIncident
import com.example.core.model.SystemIncidentAudit
import com.example.core.model.TaskGroup
import com.example.domain.repository.SystemIncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class IncidentFilterState(
    val searchQuery: String = "",
    val myReportsOnly: Boolean = false,
    val shiftedOnly: Boolean = false,
    val lan: String? = null,
    val centre: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zone: String? = null,
    val dateRange: String? = "all",
    val reason: String? = null
)

fun <T, M> StateFlow<T>.mapState(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    mapper: (T) -> M
): StateFlow<M> = map { mapper(it) }.stateIn(
    coroutineScope,
    SharingStarted.WhileSubscribed(5000),
    mapper(value)
)

data class QuickEntryFormState(
    val selectedCentre: TaskGroup? = null,
    val stateName: String = "",
    val districtName: String = "",
    val centreCode: String = "",
    val centreName: String = "",
    val examDate: String = "",
    val examName: String = "",
    val shiftName: String = "",
    val lanName: String = "LAN 1",
    val itManagerName: String = "",
    val macAddress: String = "",
    val ipAddress: String = "",
    val lockedSystemNumber: String = "",
    val candidateShifted: Boolean = false,
    val newlyAllocatedSystemNumber: String = "",
    val incidentTime: String = "",
    val incidentTimestampMillis: Long = System.currentTimeMillis(),
    val reason: String = "System Lock",
    val customReason: String = "",
    val isSubmitting: Boolean = false,
    val validationError: String? = null,
    val successMessage: String? = null
)

class SystemIncidentViewModel(
    private val repository: SystemIncidentRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(0) // 0=Quick Entry, 1=History, 2=Reports
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _formState = MutableStateFlow(QuickEntryFormState())
    val formState: StateFlow<QuickEntryFormState> = _formState.asStateFlow()

    private val _availableCentres = MutableStateFlow<List<TaskGroup>>(emptyList())
    val availableCentres: StateFlow<List<TaskGroup>> = _availableCentres.asStateFlow()

    // User Profile context for "My Reports" filter
    val currentUserId = MutableStateFlow("")
    val currentUserName = MutableStateFlow("")

    // Central Filter state
    private val _filterState = MutableStateFlow(IncidentFilterState())
    val filterState: StateFlow<IncidentFilterState> = _filterState.asStateFlow()

    val searchQuery: StateFlow<String> = _filterState.mapState(viewModelScope) { it.searchQuery }
    val filterMyReportsOnly: StateFlow<Boolean> = _filterState.mapState(viewModelScope) { it.myReportsOnly }
    val filterShiftedOnly: StateFlow<Boolean> = _filterState.mapState(viewModelScope) { it.shiftedOnly }
    val filterLan: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.lan }
    val filterCentre: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.centre }
    val filterCity: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.city }
    val filterSelectedState: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.state }
    val filterZone: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.zone }
    val filterDateRange: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.dateRange }
    val filterReason: StateFlow<String?> = _filterState.mapState(viewModelScope) { it.reason }

    // Audits for selected incident in detail view
    private val _selectedIncident = MutableStateFlow<SystemIncident?>(null)
    val selectedIncident: StateFlow<SystemIncident?> = _selectedIncident.asStateFlow()

    private val _incidentAudits = MutableStateFlow<List<SystemIncidentAudit>>(emptyList())
    val incidentAudits: StateFlow<List<SystemIncidentAudit>> = _incidentAudits.asStateFlow()

    val allIncidents: StateFlow<List<SystemIncident>> = repository.allIncidentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun getZoneForState(stateName: String): String {
            val clean = stateName.trim().lowercase(Locale.ROOT)
            return when {
                clean.contains("rajasthan") || clean.contains("delhi") || clean.contains("punjab") ||
                clean.contains("haryana") || clean.contains("uttar pradesh") || clean.contains("up") ||
                clean.contains("himachal") || clean.contains("uttarakhand") || clean.contains("jammu") ||
                clean.contains("kashmir") || clean.contains("chandigarh") || clean.contains("ladakh") -> "North Zone"

                clean.contains("maharashtra") || clean.contains("gujarat") || clean.contains("goa") ||
                clean.contains("mumbai") || clean.contains("pune") || clean.contains("daman") -> "West Zone"

                clean.contains("karnataka") || clean.contains("tamil") || clean.contains("kerala") ||
                clean.contains("andhra") || clean.contains("telangana") || clean.contains("bengaluru") ||
                clean.contains("hyderabad") || clean.contains("chennai") || clean.contains("puducherry") -> "South Zone"

                clean.contains("bengal") || clean.contains("kolkata") || clean.contains("bihar") ||
                clean.contains("odisha") || clean.contains("jharkhand") || clean.contains("assam") ||
                clean.contains("sikkim") || clean.contains("tripura") || clean.contains("meghalaya") -> "East Zone"

                clean.contains("madhya") || clean.contains("mp") || clean.contains("chhattisgarh") ||
                clean.contains("bhopal") || clean.contains("indore") || clean.contains("raipur") -> "Central Zone"

                else -> "North Zone"
            }
        }
    }

    val availableLans: StateFlow<List<String>> = allIncidents.mapState(viewModelScope) { list ->
        list.map { it.lanName }.distinct().sortedWith(compareBy({ 
            it.removePrefix("LAN ").trim().toIntOrNull() ?: Int.MAX_VALUE 
        }, { it }))
    }

    val availableCentreNames: StateFlow<List<String>> = allIncidents.mapState(viewModelScope) { list ->
        list.map { "${it.centreCode} · ${it.centreName}" }.distinct().sorted()
    }

    val availableCities: StateFlow<List<String>> = allIncidents.mapState(viewModelScope) { list ->
        list.map { it.districtName.ifBlank { "Jaipur" } }.distinct().sorted()
    }

    val availableStates: StateFlow<List<String>> = allIncidents.mapState(viewModelScope) { list ->
        list.map { it.stateName.ifBlank { "Rajasthan" } }.distinct().sorted()
    }

    val availableZones: List<String> = listOf("North Zone", "West Zone", "South Zone", "East Zone", "Central Zone")

    val filteredIncidents: StateFlow<List<SystemIncident>> = combine(
        allIncidents,
        _filterState
    ) { incidents, filter ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        val activeUid = currentUserId.value
        val activeUName = currentUserName.value

        incidents.filter { item ->
            // Search Query
            val q = filter.searchQuery
            val matchesQuery = q.isBlank() ||
                item.lockedSystemNumber.contains(q, ignoreCase = true) ||
                (item.newlyAllocatedSystemNumber?.contains(q, ignoreCase = true) == true) ||
                item.reason.contains(q, ignoreCase = true) ||
                item.lanName.contains(q, ignoreCase = true) ||
                item.centreName.contains(q, ignoreCase = true) ||
                item.centreCode.contains(q, ignoreCase = true) ||
                item.districtName.contains(q, ignoreCase = true) ||
                item.stateName.contains(q, ignoreCase = true) ||
                item.itManagerName.contains(q, ignoreCase = true) ||
                (item.creatorName?.contains(q, ignoreCase = true) == true)

            // My Reports Only filter
            val matchesMyReports = if (filter.myReportsOnly) {
                (activeUid.isNotBlank() && item.createdBy == activeUid) ||
                (activeUName.isNotBlank() && item.creatorName?.equals(activeUName, ignoreCase = true) == true) ||
                (activeUName.isNotBlank() && item.createdBy.equals(activeUName, ignoreCase = true))
            } else true

            // Shifted Only
            val matchesShifted = !filter.shiftedOnly || item.candidateShifted

            // LAN
            val matchesLan = filter.lan.isNullOrBlank() || item.lanName.equals(filter.lan, ignoreCase = true)

            // Centre
            val matchesCentre = filter.centre.isNullOrBlank() ||
                "${item.centreCode} · ${item.centreName}".equals(filter.centre, ignoreCase = true) ||
                item.centreName.contains(filter.centre, ignoreCase = true) ||
                item.centreCode.equals(filter.centre, ignoreCase = true)

            // City
            val matchesCity = filter.city.isNullOrBlank() || item.districtName.equals(filter.city, ignoreCase = true)

            // State
            val matchesState = filter.state.isNullOrBlank() || item.stateName.equals(filter.state, ignoreCase = true)

            // Zone
            val itemZone = getZoneForState(item.stateName)
            val matchesZone = filter.zone.isNullOrBlank() || itemZone.equals(filter.zone, ignoreCase = true)

            // Date Range
            val matchesDate = when (filter.dateRange) {
                "today" -> item.examDate == todayStr
                "yesterday" -> item.examDate == yesterdayStr
                else -> true
            }

            // Reason
            val matchesReason = filter.reason.isNullOrBlank() || item.reason.equals(filter.reason, ignoreCase = true)

            matchesQuery && matchesMyReports && matchesShifted && matchesLan &&
                matchesCentre && matchesCity && matchesState && matchesZone &&
                matchesDate && matchesReason
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCentresAndInitialize()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val uid = repository.getActiveUserId()
            val uname = repository.getActiveUserName()
            currentUserId.value = uid
            currentUserName.value = uname
        }
    }

    fun refreshIncidents() {
        viewModelScope.launch {
            repository.fetchAndSyncIncidentsFromFirestore()
        }
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun setFilterMyReportsOnly(myOnly: Boolean) {
        _filterState.value = _filterState.value.copy(myReportsOnly = myOnly)
    }

    fun setFilterShiftedOnly(shiftedOnly: Boolean) {
        _filterState.value = _filterState.value.copy(shiftedOnly = shiftedOnly)
    }

    fun setFilterLan(lan: String?) {
        _filterState.value = _filterState.value.copy(lan = lan)
    }

    fun setFilterCentre(centre: String?) {
        _filterState.value = _filterState.value.copy(centre = centre)
    }

    fun setFilterCity(city: String?) {
        _filterState.value = _filterState.value.copy(city = city)
    }

    fun setFilterState(state: String?) {
        _filterState.value = _filterState.value.copy(state = state)
    }

    fun setFilterZone(zone: String?) {
        _filterState.value = _filterState.value.copy(zone = zone)
    }

    fun setFilterDateRange(dateRange: String?) {
        _filterState.value = _filterState.value.copy(dateRange = dateRange)
    }

    fun setFilterReason(reason: String?) {
        _filterState.value = _filterState.value.copy(reason = reason)
    }

    fun clearAllFilters() {
        _filterState.value = IncidentFilterState()
    }

    fun selectTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun loadCentresAndInitialize() {
        viewModelScope.launch {
            val centres = repository.getAvailableCentres()
            _availableCentres.value = centres

            val defaultCentre = repository.resolveDefaultCentre()
            val userId = repository.getActiveUserId()

            val now = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            val currentDateStr = dateFormat.format(now.time)
            val currentTimeStr = timeFormat.format(now.time)

            if (defaultCentre != null) {
                applyCentre(defaultCentre, userId, currentDateStr, currentTimeStr)
            } else {
                _formState.value = _formState.value.copy(
                    examDate = currentDateStr,
                    examName = "Staff Selection Commission Examination",
                    shiftName = "Shift 1",
                    lanName = "LAN 1",
                    incidentTime = currentTimeStr,
                    incidentTimestampMillis = System.currentTimeMillis()
                )
            }
        }
    }

    fun onCentreSelected(centre: TaskGroup) {
        viewModelScope.launch {
            val userId = repository.getActiveUserId()
            repository.rememberSelectedCentre(centre.id)
            val now = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            applyCentre(centre, userId, dateFormat.format(now.time), timeFormat.format(now.time))
        }
    }

    fun createAndSelectCentre(code: String, name: String, district: String, state: String) {
        viewModelScope.launch {
            val newCentre = repository.createCentre(code, name, district, state)
            val centres = repository.getAvailableCentres()
            _availableCentres.value = centres
            onCentreSelected(newCentre)
        }
    }

    fun updateCentre(centreId: String, code: String, name: String, district: String, state: String) {
        viewModelScope.launch {
            val updated = repository.updateCentre(centreId, code, name, district, state)
            val centres = repository.getAvailableCentres()
            _availableCentres.value = centres
            onCentreSelected(updated)
        }
    }

    private suspend fun applyCentre(
        centre: TaskGroup,
        userId: String,
        currentDateStr: String,
        currentTimeStr: String
    ) {
        val (exam, shift) = repository.resolveDefaultExamAndShift(centre.id)
        val defaultLan = repository.resolveDefaultLan(userId, centre.id)
        val defaultItManager = repository.resolveDefaultItManager(userId, centre.id)

        val locParts = centre.description?.split(",")?.map { it.trim() } ?: emptyList()
        val district = if (locParts.size >= 2) locParts[locParts.size - 2] else locParts.firstOrNull() ?: "Jaipur"
        val state = if (locParts.isNotEmpty()) locParts.last() else "Rajasthan"
        val code = centre.code ?: centre.title.substringBefore("·").trim().filter { it.isDigit() }.ifBlank { "141" }
        val name = if (centre.title.contains("·")) centre.title.substringAfter("·").trim() else centre.title

        _formState.value = _formState.value.copy(
            selectedCentre = centre,
            stateName = state,
            districtName = district,
            centreCode = code,
            centreName = name,
            examDate = currentDateStr,
            examName = exam,
            shiftName = shift,
            lanName = defaultLan,
            itManagerName = defaultItManager,
            incidentTime = if (_formState.value.incidentTime.isBlank()) currentTimeStr else _formState.value.incidentTime,
            incidentTimestampMillis = System.currentTimeMillis(),
            validationError = null
        )
    }

    fun onExamDateChanged(date: String) {
        _formState.value = _formState.value.copy(examDate = date, validationError = null)
    }

    fun onExamNameChanged(name: String) {
        _formState.value = _formState.value.copy(examName = name, validationError = null)
    }

    fun onShiftNameChanged(shift: String) {
        _formState.value = _formState.value.copy(shiftName = shift, validationError = null)
    }

    fun onLanNameChanged(lan: String) {
        _formState.value = _formState.value.copy(lanName = lan, validationError = null)
    }

    fun onItManagerNameChanged(name: String) {
        _formState.value = _formState.value.copy(itManagerName = name, validationError = null)
    }

    fun onMacAddressChanged(raw: String) {
        val formatted = SystemIncident.formatMacAddress(raw)
        _formState.value = _formState.value.copy(macAddress = formatted, validationError = null)
    }

    fun onIpAddressChanged(ip: String) {
        _formState.value = _formState.value.copy(ipAddress = ip.trim(), validationError = null)
    }

    fun onLockedSystemNumberChanged(number: String) {
        _formState.value = _formState.value.copy(lockedSystemNumber = number, validationError = null)
    }

    fun onCandidateShiftedChanged(shifted: Boolean) {
        _formState.value = _formState.value.copy(
            candidateShifted = shifted,
            newlyAllocatedSystemNumber = if (!shifted) "" else _formState.value.newlyAllocatedSystemNumber,
            validationError = null
        )
    }

    fun onNewlyAllocatedSystemNumberChanged(number: String) {
        _formState.value = _formState.value.copy(newlyAllocatedSystemNumber = number, validationError = null)
    }

    fun onIncidentTimeChanged(time: String, millis: Long) {
        _formState.value = _formState.value.copy(
            incidentTime = time,
            incidentTimestampMillis = millis,
            validationError = null
        )
    }

    fun onReasonChanged(reason: String) {
        _formState.value = _formState.value.copy(reason = reason, validationError = null)
    }

    fun onCustomReasonChanged(custom: String) {
        _formState.value = _formState.value.copy(customReason = custom, validationError = null)
    }

    fun clearMessages() {
        _formState.value = _formState.value.copy(validationError = null, successMessage = null)
    }

    fun saveIncident(saveAndNext: Boolean) {
        val state = _formState.value
        val normalizedLocked = SystemIncident.normalizeSystemNumber(state.lockedSystemNumber)
        val normalizedAllocated = if (state.candidateShifted) {
            SystemIncident.normalizeSystemNumber(state.newlyAllocatedSystemNumber)
        } else {
            null
        }

        // Validate Locked System
        if (!SystemIncident.isValidSystemNumber(normalizedLocked)) {
            _formState.value = state.copy(
                validationError = "Locked System Number must follow CXXX format (e.g. C005, C105)."
            )
            return
        }

        // Validate Replacement if Shifted
        if (state.candidateShifted) {
            if (normalizedAllocated == null || !SystemIncident.isValidSystemNumber(normalizedAllocated)) {
                _formState.value = state.copy(
                    validationError = "Newly Allocated System Number is required when Candidate Shifted is Yes (e.g. C159)."
                )
                return
            }
            if (normalizedLocked.equals(normalizedAllocated, ignoreCase = true)) {
                _formState.value = state.copy(
                    validationError = "Locked System and Newly Allocated System cannot be identical."
                )
                return
            }
        }

        // Validate MAC Address
        if (!SystemIncident.isValidMacAddress(state.macAddress)) {
            _formState.value = state.copy(
                validationError = "Please enter a valid 48-bit MAC Address (e.g. AA:BB:CC:DD:EE:FF)."
            )
            return
        }

        // Validate IP Address
        if (!SystemIncident.isValidIpv4(state.ipAddress)) {
            _formState.value = state.copy(
                validationError = "Please enter a valid IPv4 address (e.g. 192.168.1.10, octets 0-255)."
            )
            return
        }

        val effectiveReason = if (state.reason.equals("Other", ignoreCase = true)) {
            state.customReason.trim()
        } else {
            state.reason.trim()
        }

        if (effectiveReason.isBlank()) {
            _formState.value = state.copy(validationError = "Please specify the reason for system failure.")
            return
        }

        if (state.itManagerName.trim().isBlank()) {
            _formState.value = state.copy(validationError = "IT Manager Name cannot be blank.")
            return
        }

        val centreId = state.selectedCentre?.id ?: "default_centre"
        val incident = SystemIncident(
            id = UUID.randomUUID().toString(),
            submissionId = "SUB-${System.currentTimeMillis()}-${(100..999).random()}",
            stateName = state.stateName.ifBlank { "Rajasthan" },
            districtName = state.districtName.ifBlank { "Jaipur" },
            centreCode = state.centreCode.ifBlank { "141" },
            centreName = state.centreName.ifBlank { "Yagyavalkya Institute of Technology" },
            centreId = centreId,
            examDate = state.examDate.ifBlank {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            },
            examName = state.examName.ifBlank { "Staff Selection Commission Examination" },
            shiftName = state.shiftName.ifBlank { "Shift 1" },
            lanName = state.lanName.ifBlank { "LAN 1" },
            macAddress = state.macAddress.uppercase(Locale.ROOT),
            ipAddress = state.ipAddress,
            lockedSystemNumber = normalizedLocked,
            newlyAllocatedSystemNumber = normalizedAllocated,
            candidateShifted = state.candidateShifted,
            incidentTime = state.incidentTime.ifBlank {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            },
            incidentTimestampMillis = state.incidentTimestampMillis,
            reason = effectiveReason,
            itManagerName = state.itManagerName.trim(),
            syncStatus = "PENDING"
        )

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isSubmitting = true, validationError = null)
            val result = repository.saveIncident(incident)
            _formState.value = _formState.value.copy(isSubmitting = false)

            result.fold(
                onSuccess = { saved ->
                    val successMsg = "Incident for system ${saved.lockedSystemNumber} recorded successfully."
                    if (saveAndNext) {
                        // Reset per-incident manual fields, preserve centre/exam/shift/lan/itManager
                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        _formState.value = _formState.value.copy(
                            macAddress = "",
                            ipAddress = "",
                            lockedSystemNumber = "",
                            candidateShifted = false,
                            newlyAllocatedSystemNumber = "",
                            incidentTime = timeFormat.format(Date()),
                            incidentTimestampMillis = System.currentTimeMillis(),
                            reason = "System Lock",
                            customReason = "",
                            validationError = null,
                            successMessage = successMsg
                        )
                    } else {
                        _formState.value = _formState.value.copy(
                            validationError = null,
                            successMessage = successMsg
                        )
                        // Transition to history tab to view saved incident
                        _currentTab.value = 1
                    }
                },
                onFailure = { err ->
                    _formState.value = _formState.value.copy(
                        validationError = err.message ?: "Failed to save incident record."
                    )
                }
            )
        }
    }

    fun selectIncidentForDetail(incident: SystemIncident) {
        _selectedIncident.value = incident
        viewModelScope.launch {
            _incidentAudits.value = repository.getAuditsForIncident(incident.id)
        }
    }

    fun dismissIncidentDetail() {
        _selectedIncident.value = null
        _incidentAudits.value = emptyList()
    }

    fun exportReport() {
        val list = filteredIncidents.value.ifEmpty { allIncidents.value }
        viewModelScope.launch {
            repository.exportIncidentsToCsvAndShare(list)
        }
    }

    fun syncPending() {
        viewModelScope.launch {
            repository.syncPendingIncidents()
        }
    }

    fun deleteIncident(incidentId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteIncident(incidentId)
            if (result.isSuccess) {
                if (_selectedIncident.value?.id == incidentId) {
                    dismissIncidentDetail()
                }
                onSuccess()
            }
        }
    }
}
