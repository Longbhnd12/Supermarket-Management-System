import { useEffect, useState } from 'react';
import categoryService from '../services/categoryService';

export default function useCategories() {
    const [categories, setCategories] = useState([]);
    const [categoryError, setCategoryError] = useState(null);

    useEffect(() => {
        let active = true;

        const loadCategories = async () => {
            try {
                const response = await categoryService.getAll();
                if (active) {
                    setCategories(response.data?.data || []);
                    setCategoryError(null);
                }
            } catch (error) {
                if (active) {
                    setCategories([]);
                    setCategoryError(error.response?.data?.message || 'Could not load categories.');
                }
            }
        };

        void loadCategories();
        return () => { active = false; };
    }, []);

    return { categories, categoryError };
}
