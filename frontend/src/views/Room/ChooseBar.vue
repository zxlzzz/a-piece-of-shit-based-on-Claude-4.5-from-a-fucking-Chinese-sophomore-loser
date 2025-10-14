<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  options: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const selected = ref(props.modelValue)

watch(() => props.modelValue, (newVal) => {
  selected.value = newVal
})

const handleSelect = (key) => {
  if (props.disabled) return
  selected.value = key
  emit('update:modelValue', key)
}
</script>

<template>
  <div class="space-y-3">
    <button
      v-for="option in options"
      :key="option.key"
      @click="handleSelect(option.key)"
      :disabled="disabled"
      class="w-full p-4 rounded-xl text-left transition-all border-2"
      :class="[
        selected === option.key
          ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-500 dark:border-blue-600'
          : 'bg-gray-50 dark:bg-gray-700/50 border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500',
        disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'
      ]"
    >
      <div class="flex items-center gap-3">
        <span class="w-8 h-8 rounded-lg flex items-center justify-center text-sm font-bold shrink-0"
              :class="selected === option.key
                ? 'bg-blue-600 text-white'
                : 'bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-300'">
          {{ option.key }}
        </span>
        <span class="flex-1 text-gray-800 dark:text-gray-200 font-medium">
          {{ option.text }}
        </span>
      </div>
    </button>
  </div>
</template>