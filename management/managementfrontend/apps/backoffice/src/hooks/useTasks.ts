import { useState, useCallback } from 'react';
import { taskService } from '@/services/taskService';
import { DEFAULT_PAGE_SIZE } from '@/config/pagination';
import type { TaskResponse, TaskListFilters } from '@/types/task';

export const useTasks = () => {
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    currentPage: 0,
    totalPages: 0,
    totalElements: 0,
    pageSize: DEFAULT_PAGE_SIZE,
  });
  const [filters, setFilters] = useState<TaskListFilters>({
    q: '',
    status: null,
    assigneeId: null,
    page: 0,
    size: DEFAULT_PAGE_SIZE,
  });

  const fetchTasks = useCallback(async (newFilters?: Partial<TaskListFilters>) => {
    setLoading(true);
    try {
      const filterState = newFilters ? { ...filters, ...newFilters } : filters;
      const response = await taskService.list(filterState);

      setTasks(response.content);
      setPagination({
        currentPage: response.page.number,
        totalPages: response.page.totalPages,
        totalElements: response.page.totalElements,
        pageSize: response.page.size,
      });
      if (newFilters) setFilters(filterState);
    } catch (error) {
      console.error('Failed to fetch tasks:', error);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const handleFilterChange = useCallback((newFilters: Partial<TaskListFilters>) => {
    const updated = { ...filters, ...newFilters, page: 0 };
    setFilters(updated);
    fetchTasks(updated);
  }, [filters, fetchTasks]);

  const handlePageChange = useCallback((page: number) => {
    const updated = { ...filters, page };
    setFilters(updated);
    fetchTasks(updated);
  }, [filters, fetchTasks]);

  return {
    tasks,
    loading,
    pagination,
    filters,
    fetchTasks,
    handleFilterChange,
    handlePageChange,
  };
};
