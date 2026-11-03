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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

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

    // History filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterShiftedOnly = MutableStateFlow(false)
    val filterShiftedOnly: StateFlow<Boolean> = _filterShiftedOnly.asStateFlow()

    private val _filterLan = MutableStateFlow<String?>(null)
    val filterLan: StateFlow<String?> = _filterLan.asStateFlow()

    private val _filterReason = MutableStateFlow<String?>(null)
    val filterReason: StateFlow<String?> = _filterReason.asStateFlow()

    // Audits for selected incident in detail view
    private val _selectedIncident = MutableStateFlow<SystemIncident?>(null)
    val selectedIncident: StateFlow<SystemIncident?> = _selectedIncident.asStateFlow()

    private val _incidentAudits = MutableStateFlow<List<SystemIncidentAudit>>(emptyList())
    val incidentAudits: StateFlow<List<SystemIncidentAudit>> = _incidentAudits.asStateFlow()

    val allIncidents: StateFlow<List<SystemIncident>> = repository.allIncidentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredIncidents: StateFlow<List<SystemIncident>> = combine(
        allIncidents,
        _searchQuery,
        _filterShiftedOnly,
        _filterLan,
        _filterReason
    ) { incidents, query, shiftedOnly, lan, reason ->
        incidents.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.lockedSystemNumber.contains(query, ignoreCase = true) ||
                (item.newlyAllocatedSystemNumber?.contains(query, ignoreCase = true) == true) ||
                item.reason.contains(query, ignoreCase = true) ||
                item.lanName.contains(query, ignoreCase = true) ||
                item.centreName.contains(query, ignoreCase = true) ||
                item.centreCode.contains(query, ignoreCase = true) ||
                item.itManagerName.contains(query, ignoreCase = true)

            val matchesShifted = !shiftedOnly || item.candidateShifted
            val matchesLan = lan.isNullOrBlank() || item.lanName.equals(lan, ignoreCase = true)
            val matchesReason = reason.isNullOrBlank() || item.reason.equals(reason, ignoreCase = true)

            matchesQuery && matchesShifted && matchesLan && matchesReason
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCentresAndInitialize()
    }

    fun selectTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterShiftedOnly(shiftedOnly: Boolean) {
        _filterShiftedOnly.value = shiftedOnly
    }

    fun setFilterLan(lan: String?) {
        _filterLan.value = lan
    }

    fun setFilterReason(reason: String?) {
        _filterReason.value = reason
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

    private suspend fun applyCentre(
        centre: TaskGroup,
        userId: String,
        currentDateStr: String,
        currentTimeStr: String
    ) {
        val (exam, shift) = repository.resolveDefaultExamAndShift(centre.id)
        val defaultLan = repository.resolveDefaultLan(userId, centre.id)
        val defaultItManager = repository.resolveDefaultItManager(userId, centre.id)

        val state = "Rajasthan"
        val district = "Jaipur"
        val code = centre.code ?: centre.title.filter { it.isDigit() }.ifBlank { "141" }

        _formState.value = _formState.value.copy(
            selectedCentre = centre,
            stateName = state,
            districtName = district,
            centreCode = code,
            centreName = centre.title,
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
}
