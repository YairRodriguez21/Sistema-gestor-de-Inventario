package com.sapam.inventario.repository;

import com.sapam.inventario.entity.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends RepositorioSGIA<Usuario, Integer> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findAll();

    Optional<Usuario> findById(Integer id);

    <S extends Usuario> S save(S usuario);
}
