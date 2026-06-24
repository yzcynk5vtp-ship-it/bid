<template>
  <el-select
    v-model="selectedId"
    :filterable="mode === 'search'"
    :remote="mode === 'search'"
    :remote-method="handleRemoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    @change="handleChange"
  >
    <el-option
      v-for="user in options"
      :key="user.id"
      :label="formatLabel(user)"
      :value="user.id"
    />
    <template v-if="options.length === 0" #empty>
      无匹配用户
    </template>
  </el-select>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useUserPicker } from '@/composables/useUserPicker.js'

const props = defineProps({
  modelValue: { type: Number, default: null },
  mode: { type: String, default: 'search' },
  context: { type: String, default: '' },
  deptCode: { type: String, default: '' },
  roleCode: { type: String, default: '' },
  placeholder: { type: String, default: '请选择用户' },
})

const emit = defineEmits(['update:modelValue', 'select'])

const { options, loading, search, loadCandidates, formatLabel } = useUserPicker({
  mode: props.mode,
  context: props.context,
  deptCode: props.deptCode,
  roleCode: props.roleCode,
})

const selectedId = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  if (val !== selectedId.value) {
    selectedId.value = val
  }
})

function handleRemoteSearch(query) {
  if (props.mode === 'search') {
    search(query)
  }
}

function handleChange(value) {
  const selected = options.value.find((user) => user.id === value)
  emit('update:modelValue', value)
  if (selected) {
    emit('select', selected)
  }
}

onMounted(() => {
  if (props.mode === 'candidates') {
    loadCandidates()
  }
})
</script>
