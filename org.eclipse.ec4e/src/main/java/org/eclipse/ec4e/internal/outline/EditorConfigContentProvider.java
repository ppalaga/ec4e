package org.eclipse.ec4e.internal.outline;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ec4e.utils.EditorUtils;
import org.eclipse.ec4j.core.EditorConfigConstants;
import org.eclipse.ec4j.core.Resources;
import org.eclipse.ec4j.core.model.EditorConfig;
import org.eclipse.ec4j.core.model.Property;
import org.eclipse.ec4j.core.model.Section;
import org.eclipse.ec4j.core.model.Version;
import org.eclipse.ec4j.core.model.propertytype.PropertyTypeRegistry;
import org.eclipse.ec4j.core.parser.EditorConfigModelHandler;
import org.eclipse.ec4j.core.parser.EditorConfigParser;
import org.eclipse.ec4j.core.parser.LocationAwareModelHandler;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonContentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class EditorConfigContentProvider
		implements ICommonContentProvider, ITreeContentProvider, IDocumentListener, IResourceChangeListener {

	public static final Object COMPUTING = new Object();

	private TreeViewer viewer;
	private ITextEditor info;
	private IFile resource;
	private EditorConfig editorConfig;
	private CompletableFuture<EditorConfig> symbols;

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TreeViewer) viewer;
		this.info = (ITextEditor) newInput;
		getDocument().addDocumentListener(this);
		resource = EditorUtils.getFile(info);
		resource.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
		refreshTreeContentFromLS();
	}

	public IDocument getDocument() {
		return info.getDocumentProvider().getDocument(info.getEditorInput());
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		refreshTreeContentFromLS();
	}

	private void refreshTreeContentFromLS() {
		if (symbols != null && !symbols.isDone()) {
			symbols.cancel(true);
		}
		symbols = CompletableFuture.supplyAsync(() -> {
			this.editorConfig = parse(getDocument());
			return editorConfig;
		});
		symbols.thenAccept(e -> {
			viewer.getControl().getDisplay().asyncExec(() -> {
				viewer.refresh();
			});
		});
	}

	private EditorConfig parse(IDocument document) {
		EditorConfigModelHandler handler = new LocationAwareModelHandler(PropertyTypeRegistry.getDefault(),
				Version.CURRENT);
		EditorConfigParser parser = EditorConfigParser.builder().build();
		try {
			parser.parse(Resources.ofString(EditorConfigConstants.EDITORCONFIG, document.get()), handler);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return handler.getEditorConfig();
	}

	@Override
	public Object[] getElements(Object inputElement) {
		if (this.symbols != null && !this.symbols.isDone()) {
			return new Object[] { COMPUTING };
		}
		if (editorConfig != null) {
			return editorConfig.getSections().toArray();
		}
		return null;
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof Section) {
			return ((Section) parentElement).getProperties().toArray();
		}
		return null;
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof Property) {
			// return ((Property) element).
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof Section) {
			return ((Section) element).getProperties().size() > 0;
		}
		return false;
	}

	@Override
	public void dispose() {
		getDocument().removeDocumentListener(this);
		resource.getWorkspace().removeResourceChangeListener(this);
		ICommonContentProvider.super.dispose();
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if ((event.getDelta().getFlags() ^ IResourceDelta.MARKERS) != 0) {
			try {
				event.getDelta().accept(delta -> {
					if (delta.getResource().equals(this.resource)) {
						viewer.getControl().getDisplay().asyncExec(() -> {
							if (viewer instanceof StructuredViewer) {
								viewer.refresh(true);
							}
						});
					}
					return delta.getResource().getFullPath().isPrefixOf(this.resource.getFullPath());
				});
			} catch (CoreException e) {
				// LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void init(ICommonContentExtensionSite aConfig) {
	}

	@Override
	public void restoreState(IMemento aMemento) {
	}

	@Override
	public void saveState(IMemento aMemento) {
	}

}