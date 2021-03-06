package org.springframework.data.jpa.datatables;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.hibernate.query.criteria.internal.path.AbstractPathImpl;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

public class SpecificationBuilder<T> extends AbstractPredicateBuilder<Specification<T>> {
	public SpecificationBuilder(DataTablesInput input) {
		super(input);
	}

	@Override
	public Specification<T> build() {
		return new DataTablesSpecification<>();
	}

	private class DataTablesSpecification<S> implements Specification<S> {
		private static final long serialVersionUID = -1541802284737654776L;
		private List<Predicate> columnPredicates = new ArrayList<>();
		private List<Predicate> globalPredicates = new ArrayList<>();

		@Override
		public Predicate toPredicate(@NonNull Root<S> root, @NonNull CriteriaQuery<?> query,
				@NonNull CriteriaBuilder criteriaBuilder) {
			initPredicatesRecursively(tree, root, root, criteriaBuilder);

			boolean isCountQuery = query.getResultType() == Long.class;
			if (isCountQuery) {
				root.getFetches().clear();
			}

			return createFinalPredicate(criteriaBuilder);
		}

		@SuppressWarnings("unchecked")
		private void initPredicatesRecursively(Node<Filter> node, From<S, S> from, FetchParent<S, S> fetch,
				CriteriaBuilder criteriaBuilder) {
			if (node.isLeaf()) {
				boolean hasColumnFilter = node.getData() != null;
				if (hasColumnFilter) {
					Filter columnFilter = node.getData();
					columnPredicates.add(columnFilter.createPredicate(from, criteriaBuilder, node.getName()));
				} else if (hasGlobalFilter) {
					Filter globalFilter = tree.getData();
					globalPredicates.add(globalFilter.createPredicate(from, criteriaBuilder, node.getName()));
				}
			}
			for (Node<Filter> child : node.getChildren()) {
				String pname = child.getName();
				Path<Object> path = from.get(pname);
				if (path instanceof AbstractPathImpl) {
					if (((AbstractPathImpl<?>) path).getAttribute().isCollection()) {
						// ignore OneToMany and ManyToMany relationships
						continue;
					}
				}
				if (child.isLeaf()) {
					initPredicatesRecursively(child, from, fetch, criteriaBuilder);
				} else {
					Join<S, S> join = from.join(pname, JoinType.LEFT);
					Fetch<S, S> ff = null;
					Set<Fetch<S, ?>> fetches = fetch.getFetches();
					for (Fetch<S, ?> f : fetches) {
						if (f.getAttribute().getName().equals(pname)) {
							ff = (Fetch<S, S>) f;
						}

					}
					Fetch<S, S> childFetch = ff == null ? fetch.fetch(pname, JoinType.LEFT) : ff;
					initPredicatesRecursively(child, join, childFetch, criteriaBuilder);
				}
			}
		}

		private Predicate createFinalPredicate(CriteriaBuilder criteriaBuilder) {
			List<Predicate> allPredicates = new ArrayList<>(columnPredicates);

			if (!globalPredicates.isEmpty()) {
				allPredicates.add(criteriaBuilder.or(globalPredicates.toArray(new Predicate[0])));
			}

			return allPredicates.isEmpty() ? criteriaBuilder.conjunction()
					: criteriaBuilder.and(allPredicates.toArray(new Predicate[0]));
		}
	}

}