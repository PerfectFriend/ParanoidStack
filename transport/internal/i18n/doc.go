// Package i18n provides multi-language support for the px-transport server.
// It implements a Translator with built-in messages for English, Russian, and Spanish,
// JSON file persistence for custom overrides, and a Global singleton convenience wrapper.
// Key types include Translator (T, SetCustom, Languages methods) and the top-level
// T() function for quick lookup with fallback chains.
package i18n
