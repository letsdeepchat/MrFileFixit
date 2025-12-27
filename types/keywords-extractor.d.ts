declare module 'keywords-extractor' {
  interface ExtractionConfig {
    language?: string;
    remove_digits?: boolean;
    return_changed_case?: boolean;
    remove_duplicates?: boolean;
  }
  
  export function extract(text: string, config?: ExtractionConfig): string[];
}